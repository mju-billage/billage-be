package com.billage.group;

import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dues.DuesService;
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
import com.billage.report.ReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupService {

	private final GroupSpaceRepository groupSpaceRepository;
	private final GroupMembershipRepository groupMembershipRepository;
	private final GroupInvitationRepository groupInvitationRepository;
	private final MemberRepository memberRepository;
	private final DuesService duesService;
	private final EntryRepository entryRepository;
	private final FileService fileService;
	private final LedgerRepository ledgerRepository;
	private final FolderRepository folderRepository;
	private final ReportRepository reportRepository;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public List<GroupSummaryResponse> getMyGroups(Long userId) {
		return groupMembershipRepository.findMyGroups(userId).stream()
				.map(row -> GroupSummaryResponse.of(row, fileService.contentUrl(row.groupImageFileId())))
				.toList();
	}

	/**
	 * 모임 생성. 생성자는 최초 총무(OWNER)로 자동 등록된다.
	 * 납부 명단(Member)은 만들지 않는다 — 총무가 [모임원 추가]로 따로 등록한다.
	 */
	@Transactional
	public GroupCreateResponse create(Long userId, GroupCreateRequest request) {
		Long imageFileId = request.groupImageFileId();
		if (imageFileId != null) {
			fileService.requireUsableGroupImage(imageFileId, userId);
		}
		GroupSpace group;
		try {
			group = groupSpaceRepository.saveAndFlush(
					GroupSpace.create(request.name().trim(), request.description(), imageFileId, userId));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.FILE_IN_USE);
		}
		groupMembershipRepository.save(GroupMembership.createOwner(group, userId));

		return GroupCreateResponse.of(group, fileService.contentUrl(imageFileId));
	}

	@Transactional(readOnly = true)
	public GroupDetailResponse getDetail(Long groupId, Long userId) {
		GroupMembership me = guard.requireMembership(groupId, userId);
		long memberCount = memberRepository.countByGroupId(groupId);
		long ownerCount = groupMembershipRepository.countByGroupIdAndRole(groupId, GroupRole.OWNER);

		return GroupDetailResponse.of(me.getGroup(), fileService.contentUrl(me.getGroup().getGroupImageFileId()),
				me.getRole(), memberCount, ownerCount);
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
		if (request.imageChangeRequested()) {
			replaceImage(group, request.targetImageFileId(), userId);
		}

		return GroupUpdateResponse.of(group, fileService.contentUrl(group.getGroupImageFileId()));
	}

	/**
	 * 대표 이미지 교체. 새 파일이 null 이면 기본 이미지로 되돌린다.
	 * 쓰이지 않게 된 이전 이미지는 저장소에 남기지 않는다.
	 */
	private void replaceImage(GroupSpace group, Long newFileId, Long userId) {
		Long previousFileId = group.getGroupImageFileId();
		if (Objects.equals(previousFileId, newFileId)) {
			return;
		}
		if (newFileId != null) {
			fileService.requireUsableGroupImage(newFileId, userId);
		}
		// 참조를 먼저 옮긴 뒤 이전 파일을 지운다. 반대로 하면 삭제 실패 시 깨진 참조만 남는다.
		group.changeImage(newFileId);
		try {
			// 두 모임이 같은 파일을 동시에 집어 가는 경합은 유니크 제약이 막는다. 500 대신 409 로 돌려준다.
			groupSpaceRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.FILE_IN_USE);
		}
		fileService.deleteGroupImage(previousFileId);
	}

	/**
	 * 모임 완전 삭제. 종속 데이터(관리자 관계·납부 명단·초대 코드·폴더·장부·내역·증빙·보고서·회비)를 함께 물리 삭제하며
	 * 복구용 이력은 남기지 않는다.
	 */
	@Transactional
	public void delete(Long groupId, Long userId) {
		guard.requireOwner(groupId, userId);

		groupInvitationRepository.deleteByGroupId(groupId);
		// 회비가 납부 명단을 참조하므로 명단보다 먼저 지운다.
		duesService.deleteByGroup(groupId);
		memberRepository.deleteByGroupId(groupId);
		// 보고서는 스냅샷이라 장부·내역과 독립적이다. 자식 스냅샷은 cascade 로 함께 지워진다.
		reportRepository.deleteAll(reportRepository.findAllByGroupId(groupId));
		// 증빙 → 내역 → 장부 → 폴더 순으로 참조를 따라 지운다.
		fileService.deleteByGroup(groupId);
		entryRepository.deleteAllByGroupId(groupId);
		ledgerRepository.deleteAllByGroupId(groupId);
		folderRepository.deleteDeepestFirst(folderRepository.findAllByGroupId(groupId));
		groupMembershipRepository.deleteByGroupId(groupId);
		Long imageFileId = groupSpaceRepository.findById(groupId)
				.map(GroupSpace::getGroupImageFileId).orElse(null);
		groupSpaceRepository.deleteById(groupId);
		fileService.deleteGroupImage(imageFileId);
	}
}
