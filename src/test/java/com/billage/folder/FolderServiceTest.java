package com.billage.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.folder.dto.FolderItemResponse;
import com.billage.folder.dto.FolderTreeResponse;
import com.billage.folder.dto.FolderUpdateRequest;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerRepository;
import com.billage.ledger.LedgerService;
import com.billage.entry.EntryService;
import com.billage.entry.EntryType;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.ledger.dto.GroupLedgerResponse;
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
	EntryService entryService;
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
		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
		otherGroupId = groupService.create(adminId, new GroupCreateRequest("남의모임", null, null)).groupId();
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
		// getGroup() 을 건드리면 open-in-view=false + LAZY 라 지연 로딩에서 터질 수 있어
		// 연관을 타지 않고 단언한다(이 테스트에서 장부는 이 모임에만 있다).
		assertThat(ledgerRepository.findAll()).isEmpty();
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

		// 허용 경계도 함께 고정한다. 0 은 "예산 0원", 미설정은 null 로 구분한다.
		assertThat(ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("0원 장부", 0L)).ledgerId()).isNotNull();
		assertThat(ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("상한 장부", Ledger.MAX_BUDGET)).ledgerId()).isNotNull();
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

	// --- 폴더 화면의 한 계층 (폴더 + 장부) ---

	@Test
	void 한_계층의_폴더와_장부를_한_목록으로_주고_합산_개수를_센다() {
		Long mtId = createFolder("MT", null);
		createFolder("정기공연", null);
		createFolder("MT 하위", mtId);
		ledgerService.create(mtId, ownerId, new LedgerCreateRequest("MT 장부", null));
		Long rootLedgerId = createRootLedger("최상위 장부");

		var top = folderService.getFolderItems(groupId, ownerId, null, null);

		assertThat(top.totalCount()).isEqualTo(3);
		// 폴더가 먼저, 그다음 장부.
		assertThat(top.items()).extracting(FolderItemResponse::name)
				.containsExactly("MT", "정기공연", "최상위 장부");
		assertThat(top.items()).extracting(FolderItemResponse::itemType)
				.containsExactly(FolderItemResponse.ItemType.FOLDER, FolderItemResponse.ItemType.FOLDER,
						FolderItemResponse.ItemType.LEDGER);
		// MT 폴더의 '{N}개의 항목' 은 하위 폴더 1 + 하위 장부 1.
		assertThat(top.items().get(0).childCount()).isEqualTo(2);
		// 장부는 개수 대신 생성일을 보여 주므로 childCount 가 없다.
		assertThat(top.items().get(2).childCount()).isNull();
		assertThat(top.items().get(2).id()).isEqualTo(rootLedgerId);
	}

	@Test
	void 폴더를_해제해_최상위로_올라온_장부도_조회된다() {
		Long folderId = createFolder("MT", null);
		Long ledgerId = ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("MT 장부", null)).ledgerId();

		folderService.delete(folderId, ownerId);

		var top = folderService.getFolderItems(groupId, ownerId, null, null);
		assertThat(top.items()).singleElement()
				.satisfies(item -> assertThat(item.id()).isEqualTo(ledgerId));
	}

	@Test
	void 폴더_안으로_들어가면_그_계층만_보인다() {
		Long mtId = createFolder("MT", null);
		createFolder("정기공연", null);
		Long childId = createFolder("MT 하위", mtId);

		var inside = folderService.getFolderItems(groupId, ownerId, mtId, null);

		assertThat(inside.items()).singleElement()
				.satisfies(item -> assertThat(item.id()).isEqualTo(childId));
	}

	@Test
	void 검색어는_폴더명과_장부명에_모두_걸린다() {
		createFolder("MT", null);
		createFolder("정기공연", null);
		createRootLedger("MT 회계");

		var found = folderService.getFolderItems(groupId, ownerId, null, "MT");

		assertThat(found.totalCount()).isEqualTo(2);
		assertThat(found.items()).extracting(FolderItemResponse::name).containsExactly("MT", "MT 회계");
	}

	@Test
	void 항목이_없어도_개수와_빈_목록을_준다() {
		var empty = folderService.getFolderItems(groupId, ownerId, null, null);

		assertThat(empty.totalCount()).isZero();
		assertThat(empty.items()).isEmpty();
	}

	@Test
	void 다른_모임의_폴더_항목은_조회할_수_없다() {
		assertThatThrownBy(() -> folderService.getFolderItems(otherGroupId, ownerId, null, null))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	// --- 모임 전체 장부 목록 ---

	@Test
	void 모임_전체_장부는_폴더를_가로질러_모으고_최상위_장부도_포함한다() {
		Long mtId = createFolder("MT", null);
		Long childId = createFolder("MT 하위", mtId);
		ledgerService.create(mtId, ownerId, new LedgerCreateRequest("MT 장부", null));
		ledgerService.create(childId, ownerId, new LedgerCreateRequest("하위 장부", null));
		createRootLedger("최상위 장부");

		var ledgers = ledgerService.getGroupLedgers(groupId, ownerId, null);

		assertThat(ledgers).hasSize(3);
		// 최신 생성순(화면 「예산 설정」 규칙).
		assertThat(ledgers).extracting(GroupLedgerResponse::name)
				.containsExactly("최상위 장부", "하위 장부", "MT 장부");
		// 최상위로 올라온 장부는 폴더 정보가 비어 있다.
		assertThat(ledgers.get(0).folderId()).isNull();
		assertThat(ledgers.get(0).folderName()).isNull();
		assertThat(ledgers.get(2).folderName()).isEqualTo("MT");
	}

	@Test
	void 예산_소진율은_지출을_예산으로_나눈_값이고_예산이_없으면_비운다() {
		Long folderId = createFolder("MT", null);
		Long budgeted = ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("예산 장부", 1_000_000L)).ledgerId();
		ledgerService.create(folderId, ownerId, new LedgerCreateRequest("예산 없는 장부", null));
		entryService.create(budgeted, ownerId, new EntryCreateRequest(EntryType.EXPENSE, "대관료", 250_000L,
				LocalDate.of(2026, 7, 20), null, null));

		var byId = ledgerService.getGroupLedgers(groupId, ownerId, null).stream()
				.collect(java.util.stream.Collectors.toMap(GroupLedgerResponse::ledgerId, l -> l));

		assertThat(byId.get(budgeted).budgetUsageRate()).isEqualByComparingTo("25.00");
		assertThat(byId.get(budgeted).remainingBudget()).isEqualTo(750_000L);
		assertThat(byId.values().stream().filter(l -> l.budget() == null).findFirst().orElseThrow()
				.budgetUsageRate()).isNull();
	}

	/** 최상위 영역(어느 폴더에도 속하지 않는) 장부. 폴더를 만들고 바로 해제해서 만든다. */
	private Long createRootLedger(String name) {
		Long tempFolderId = createFolder("임시", null);
		Long ledgerId = ledgerService.create(tempFolderId, ownerId, new LedgerCreateRequest(name, null)).ledgerId();
		folderService.delete(tempFolderId, ownerId);
		return ledgerId;
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
