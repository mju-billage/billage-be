package com.billage.group;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.group.dto.GroupCreateRequest;
import com.billage.group.dto.GroupCreateResponse;
import com.billage.group.dto.GroupDetailResponse;
import com.billage.group.dto.GroupSummaryResponse;
import com.billage.group.dto.GroupUpdateRequest;
import com.billage.group.dto.GroupUpdateResponse;
import com.billage.entry.EntryRepository;
import com.billage.file.FileService;
import com.billage.folder.FolderRepository;
import com.billage.ledger.LedgerRepository;
import com.billage.member.MemberRepository;
import com.billage.membership.GroupAccessGuard;
import com.billage.membership.GroupInvitationRepository;
import com.billage.membership.GroupMembership;
import com.billage.membership.GroupMembershipRepository;
import com.billage.membership.GroupRole;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupService {

	private final GroupSpaceRepository groupSpaceRepository;
	private final GroupMembershipRepository groupMembershipRepository;
	private final GroupInvitationRepository groupInvitationRepository;
	private final MemberRepository memberRepository;
	private final EntryRepository entryRepository;
	private final FileService fileService;
	private final LedgerRepository ledgerRepository;
	private final FolderRepository folderRepository;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public List<GroupSummaryResponse> getMyGroups(Long userId) {
		return groupMembershipRepository.findMyGroups(userId).stream()
				.map(GroupSummaryResponse::from)
				.toList();
	}

	/**
	 * 모임 생성. 생성자는 최초 총무(OWNER)로 자동 등록된다.
	 * 납부 명단(Member)은 만들지 않는다 — 총무가 [모임원 추가]로 따로 등록한다.
	 */
	@Transactional
	public GroupCreateResponse create(Long userId, GroupCreateRequest request) {
		GroupSpace group = groupSpaceRepository.save(
				GroupSpace.create(request.name().trim(), request.description(), userId));
		groupMembershipRepository.save(GroupMembership.createOwner(group, userId));

		return GroupCreateResponse.from(group);
	}

	@Transactional(readOnly = true)
	public GroupDetailResponse getDetail(Long groupId, Long userId) {
		GroupMembership me = guard.requireMembership(groupId, userId);
		long memberCount = memberRepository.countByGroupId(groupId);
		long ownerCount = groupMembershipRepository.countByGroupIdAndRole(groupId, GroupRole.OWNER);

		return GroupDetailResponse.of(me.getGroup(), me.getRole(), memberCount, ownerCount);
	}

	@Transactional
	public GroupUpdateResponse update(Long groupId, Long userId, GroupUpdateRequest request) {
		GroupSpace group = guard.requireOwner(groupId, userId).getGroup();
		group.update(request.name() == null ? null : request.name().trim(), request.description());

		return GroupUpdateResponse.from(group);
	}

	/**
	 * 모임 완전 삭제. 종속 데이터(관리자 관계·납부 명단·초대 코드·폴더·장부·내역·증빙)를 함께 물리 삭제하며
	 * 복구용 이력은 남기지 않는다. 회비·보고서 도메인 구현 시 이 메서드에 삭제 대상을 추가해야 한다.
	 */
	@Transactional
	public void delete(Long groupId, Long userId) {
		guard.requireOwner(groupId, userId);

		groupInvitationRepository.deleteByGroupId(groupId);
		memberRepository.deleteByGroupId(groupId);
		// 증빙 → 내역 → 장부 → 폴더 순으로 참조를 따라 지운다.
		fileService.deleteByGroup(groupId);
		entryRepository.deleteAllByGroupId(groupId);
		ledgerRepository.deleteAllByGroupId(groupId);
		folderRepository.deleteDeepestFirst(folderRepository.findAllByGroupId(groupId));
		groupMembershipRepository.deleteByGroupId(groupId);
		groupSpaceRepository.deleteById(groupId);
	}
}
