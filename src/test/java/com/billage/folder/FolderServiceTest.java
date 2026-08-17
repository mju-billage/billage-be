package com.billage.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.folder.dto.FolderTreeResponse;
import com.billage.folder.dto.FolderUpdateRequest;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerRepository;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.ledger.dto.LedgerUpdateRequest;
import com.billage.membership.GroupMembershipService;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 폴더 계층 규칙(순환 방지, 삭제 시 상위 승격)과 장부의 모임 경계 검증.
 */
class FolderServiceTest extends IntegrationTest {

	@Autowired
	GroupService groupService;
	@Autowired
	GroupMembershipService groupMembershipService;
	@Autowired
	FolderService folderService;
	@Autowired
	LedgerService ledgerService;
	@Autowired
	FolderRepository folderRepository;
	@Autowired
	LedgerRepository ledgerRepository;
	@Autowired
	UserRepository userRepository;

	private Long ownerId;
	private Long adminId;
	private Long groupId;
	private Long otherGroupId;

	@BeforeEach
	void setUp() {

		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "encoded", "일반관리자")).getId();
		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null)).groupId();
		otherGroupId = groupService.create(adminId, new GroupCreateRequest("남의모임", null)).groupId();
	}

	// --- 권한 ---

	@Test
	void 일반_관리자는_폴더를_만들_수_없다() {
		joinAsAdmin();

		assertThatThrownBy(() -> folderService.create(groupId, adminId, new FolderCreateRequest("정기공연", null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 다른_모임의_폴더는_조회할_수_없다() {
		assertThatThrownBy(() -> folderService.getFolderTree(otherGroupId, ownerId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	// --- 계층 구조 ---

	@Test
	void 폴더_트리는_하위_폴더와_장부_수를_함께_반환한다() {
		Long parentId = createFolder("2026년 상반기", null);
		createFolder("정기공연", parentId);
		ledgerService.create(parentId, ownerId, new LedgerCreateRequest("운영 장부", 3_000_000L));

		List<FolderTreeResponse> tree = folderService.getFolderTree(groupId, ownerId);

		assertThat(tree).hasSize(1);
		FolderTreeResponse root = tree.get(0);
		assertThat(root.name()).isEqualTo("2026년 상반기");
		assertThat(root.ledgerCount()).isEqualTo(1);
		assertThat(root.childFolders()).hasSize(1);
		assertThat(root.childFolders().get(0).name()).isEqualTo("정기공연");
		assertThat(root.childFolders().get(0).childFolders()).isEmpty();
	}

	@Test
	void 자기_자신을_상위_폴더로_지정할_수_없다() {
		Long folderId = createFolder("정기공연", null);

		assertThatThrownBy(() -> folderService.update(folderId, ownerId,
				new FolderUpdateRequest(null, Optional.of(folderId))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_PARENT_FOLDER);
	}

	@Test
	void 하위_폴더를_상위로_지정하면_순환이_생겨_거부된다() {
		Long parentId = createFolder("2026년 상반기", null);
		Long childId = createFolder("정기공연", parentId);

		assertThatThrownBy(() -> folderService.update(parentId, ownerId,
				new FolderUpdateRequest(null, Optional.of(childId))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_PARENT_FOLDER);
	}

	@Test
	void 다른_모임의_폴더는_상위로_지정할_수_없다() {
		Long folderId = createFolder("정기공연", null);
		Long otherFolderId = folderService.create(otherGroupId, adminId,
				new FolderCreateRequest("남의폴더", null)).folderId();

		assertThatThrownBy(() -> folderService.update(folderId, ownerId,
				new FolderUpdateRequest(null, Optional.of(otherFolderId))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_PARENT_FOLDER);
	}

	@Test
	void 상위_폴더를_null로_전달하면_최상위로_이동한다() {
		Long parentId = createFolder("2026년 상반기", null);
		Long childId = createFolder("정기공연", parentId);

		folderService.update(childId, ownerId, new FolderUpdateRequest(null, Optional.empty()));

		assertThat(folderRepository.findById(childId).orElseThrow().getParentId()).isNull();
	}

	@Test
	void 상위_폴더를_전달하지_않으면_이동하지_않는다() {
		Long parentId = createFolder("2026년 상반기", null);
		Long childId = createFolder("정기공연", parentId);

		folderService.update(childId, ownerId, new FolderUpdateRequest("여름 정기공연", null));

		Folder child = folderRepository.findById(childId).orElseThrow();
		assertThat(child.getName()).isEqualTo("여름 정기공연");
		assertThat(child.getParentId()).isEqualTo(parentId);
	}

	// --- 삭제 ---

	@Test
	void 폴더_삭제_시_하위_폴더와_장부는_상위로_올라간다() {
		Long grandParentId = createFolder("2026년", null);
		Long parentId = createFolder("상반기", grandParentId);
		Long childId = createFolder("정기공연", parentId);
		Long ledgerId = ledgerService.create(parentId, ownerId,
				new LedgerCreateRequest("운영 장부", null)).ledgerId();

		folderService.delete(parentId, ownerId);

		assertThat(folderRepository.findById(parentId)).isEmpty();
		assertThat(folderRepository.findById(childId).orElseThrow().getParentId()).isEqualTo(grandParentId);
		assertThat(ledgerRepository.findById(ledgerId).orElseThrow().getFolderId()).isEqualTo(grandParentId);
	}

	@Test
	void 최상위_폴더를_삭제하면_내부_항목은_최상위_영역으로_간다() {
		Long rootId = createFolder("2026년 상반기", null);
		Long childId = createFolder("정기공연", rootId);
		Long ledgerId = ledgerService.create(rootId, ownerId,
				new LedgerCreateRequest("운영 장부", null)).ledgerId();

		folderService.delete(rootId, ownerId);

		assertThat(folderRepository.findById(childId).orElseThrow().getParentId()).isNull();
		assertThat(ledgerRepository.findById(ledgerId).orElseThrow().getFolderId()).isNull();
	}

	@Test
	void 모임_삭제_시_폴더와_장부도_함께_삭제된다() {
		Long parentId = createFolder("2026년 상반기", null);
		createFolder("정기공연", parentId);
		ledgerService.create(parentId, ownerId, new LedgerCreateRequest("운영 장부", null));

		groupService.delete(groupId, ownerId);

		assertThat(folderRepository.findAllByGroupId(groupId)).isEmpty();
		assertThat(ledgerRepository.findAll()).noneMatch(l -> l.getGroup().getId().equals(groupId));
	}

	// --- 장부 ---

	@Test
	void 장부는_다른_모임의_폴더로_옮길_수_없다() {
		Long folderId = createFolder("정기공연", null);
		Long ledgerId = ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("운영 장부", null)).ledgerId();
		Long otherFolderId = folderService.create(otherGroupId, adminId,
				new FolderCreateRequest("남의폴더", null)).folderId();

		assertThatThrownBy(() -> ledgerService.update(ledgerId, ownerId,
				new LedgerUpdateRequest(null, otherFolderId)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.GROUP_MISMATCH);
	}

	@Test
	void 예산은_0원_이상_9억9천9백99만원_이하만_허용한다() {
		Long folderId = createFolder("정기공연", null);

		assertThatThrownBy(() -> ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("운영 장부", Ledger.MAX_BUDGET + 1)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_BUDGET);

		assertThatThrownBy(() -> ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("운영 장부", -1L)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_BUDGET);
	}

	@Test
	void 예산_미설정_장부의_잔여_예산은_null이다() {
		Long folderId = createFolder("정기공연", null);
		Long ledgerId = ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("운영 장부", null)).ledgerId();

		assertThat(ledgerService.getDetail(ledgerId, ownerId).remainingBudget()).isNull();
	}

	// --- 부분 수정 시 공백 이름 차단 ---

	@Test
	void 폴더와_장부_이름을_공백만으로_수정할_수_없다() {
		Long folderId = createFolder("정기공연", null);
		Long ledgerId = ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("운영 장부", null)).ledgerId();

		assertThatThrownBy(() -> folderService.update(folderId, ownerId,
				new FolderUpdateRequest("   ", null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);

		assertThatThrownBy(() -> ledgerService.update(ledgerId, ownerId,
				new LedgerUpdateRequest("   ", null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);

		// 이름을 생략한 부분 수정은 그대로 동작해야 한다.
		ledgerService.update(ledgerId, ownerId, new LedgerUpdateRequest(null, folderId));
		assertThat(ledgerRepository.findById(ledgerId).orElseThrow().getName()).isEqualTo("운영 장부");
		assertThat(folderRepository.findById(folderId).orElseThrow().getName()).isEqualTo("정기공연");
	}

	private Long createFolder(String name, Long parentId) {
		return folderService.create(groupId, ownerId, new FolderCreateRequest(name, parentId)).folderId();
	}

	/** 초대 코드로 참여시킨다. 참여자는 기본 MEMBER(일반 권한 관리자)다. */
	private void joinAsAdmin() {
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(adminId, code);
	}
}
