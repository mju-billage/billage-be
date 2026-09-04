package com.billage.group;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.archive.ArchiveService;
import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dues.DuesService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.group.dto.GroupCreateResponse;
import com.billage.group.dto.GroupDetailResponse;
import com.billage.group.dto.GroupListRow;
import com.billage.group.dto.GroupSummaryResponse;
import com.billage.group.dto.GroupUpdateRequest;
import com.billage.group.dto.GroupUpdateResponse;
import com.billage.entry.EntryRepository;
import com.billage.file.FileService;
import com.billage.file.UploadedFile;
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
	private final ArchiveService archiveService;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public List<GroupSummaryResponse> getMyGroups(Long userId) {
		List<GroupListRow> rows = groupMembershipRepository.findMyGroups(userId);
		Map<Long, String> images = fileService.groupImageUrls(rows.stream().map(GroupListRow::groupId).toList());

		return rows.stream()
				.map(row -> GroupSummaryResponse.of(row, images.get(row.groupId())))
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
		if (request.groupImageFileId() != null) {
			fileService.claimGroupImage(request.groupImageFileId(), group.getId(), userId);
		}

		return GroupCreateResponse.of(group, fileService.groupImageUrl(group.getId()));
	}

	@Transactional(readOnly = true)
	public GroupDetailResponse getDetail(Long groupId, Long userId) {
		GroupMembership me = guard.requireMembership(groupId, userId);
		long memberCount = memberRepository.countByGroupId(groupId);
		long ownerCount = groupMembershipRepository.countByGroupIdAndRole(groupId, GroupRole.OWNER);

		return GroupDetailResponse.of(me.getGroup(), fileService.groupImageUrl(groupId),
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
			replaceImage(groupId, request.targetImageFileId(), userId);
		}

		return GroupUpdateResponse.of(group, fileService.groupImageUrl(groupId));
	}

	/**
	 * 대표 이미지 교체. 새 파일이 null 이면 기본 이미지로 되돌린다.
	 * 쓰이지 않게 된 이전 이미지는 저장소에 남기지 않는다.
	 *
	 * <p>모임당 하나(uk_file_group)라 이전 이미지를 먼저 떼어낸 뒤 새 이미지를 선점한다.
	 * 이전 파일의 <b>물리 삭제는 선점이 성공한 뒤</b>에 한다 — 저장소 삭제는 롤백되지 않으므로,
	 * 먼저 지우면 선점 실패 시 되살아난 참조가 빈 파일을 가리킨다.
	 */
	private void replaceImage(Long groupId, Long newFileId, Long userId) {
		// 화면이 현재 이미지를 그대로 다시 보낸 경우. 떼기 전에 걸러야 한다 —
		// 떼었다 다시 붙이면 그 이미지를 올린 사람이 공동 총무일 때 본인 파일이 아니라 막힌다.
		if (fileService.findGroupImage(groupId).map(file -> file.getId().equals(newFileId)).orElse(false)) {
			return;
		}
		Optional<UploadedFile> previous = fileService.detachGroupImage(groupId);
		if (newFileId != null) {
			// 실패하면 트랜잭션째 되돌아가 이전 이미지가 그대로 남는다. 그래서 물리 삭제보다 먼저 한다.
			fileService.claimGroupImage(newFileId, groupId, userId);
		}
		previous.ifPresent(fileService::deleteDetached);
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
		// 보고서·보관 기록은 스냅샷이라 장부·내역과 독립적이다. 자식 스냅샷은 cascade 로 함께 지워진다.
		reportRepository.deleteAll(reportRepository.findAllByGroupId(groupId));
		archiveService.deleteByGroup(groupId);
		// 증빙 → 내역 → 장부 → 폴더 순으로 참조를 따라 지운다.
		fileService.deleteByGroup(groupId);
		entryRepository.deleteAllByGroupId(groupId);
		ledgerRepository.deleteAllByGroupId(groupId);
		folderRepository.deleteDeepestFirst(folderRepository.findAllByGroupId(groupId));
		groupMembershipRepository.deleteByGroupId(groupId);
		// 대표 이미지는 모임을 참조하므로 모임 행보다 먼저 지운다.
		fileService.deleteGroupImage(groupId);
		groupSpaceRepository.deleteById(groupId);
	}
}
