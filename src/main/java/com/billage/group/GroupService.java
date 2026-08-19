package com.billage.group;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.group.dto.GroupCreateResponse;
import com.billage.group.dto.GroupDetailResponse;
import com.billage.group.dto.GroupSummaryResponse;
import com.billage.group.dto.GroupUpdateRequest;
import com.billage.group.dto.GroupUpdateResponse;
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

	/**
	 * 모임 수정(부분 수정). 전달되지 않은(null) 필드는 그대로 둔다.
	 * 공백 전용 이름은 `@Size(min = 1)` 을 통과해 trim 후 빈 이름이 되므로 여기서 막는다.
	 */
	@Transactional
	public GroupUpdateResponse update(Long groupId, Long userId, GroupUpdateRequest request) {
		GroupSpace group = guard.requireOwner(groupId, userId).getGroup();
		String name = request.name() == null ? null : request.name().trim();
		if (name != null && name.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "모임 이름은 공백일 수 없습니다.");
		}
		group.update(name, request.description());

		return GroupUpdateResponse.from(group);
	}

	/**
	 * 모임 완전 삭제. 종속 데이터(관리자 관계·납부 명단·초대 코드·폴더·장부)를 함께 물리 삭제하며
	 * 복구용 이력은 남기지 않는다. 내역·회비·보고서 도메인 구현 시 이 메서드에 삭제 대상을 추가해야 한다.
	 */
	@Transactional
	public void delete(Long groupId, Long userId) {
		guard.requireOwner(groupId, userId);

		groupInvitationRepository.deleteByGroupId(groupId);
		memberRepository.deleteByGroupId(groupId);
		// 장부가 폴더를 참조하므로 장부부터 지운다.
		ledgerRepository.deleteAllByGroupId(groupId);
		folderRepository.deleteDeepestFirst(folderRepository.findAllByGroupId(groupId));
		groupMembershipRepository.deleteByGroupId(groupId);
		groupSpaceRepository.deleteById(groupId);
	}
}
