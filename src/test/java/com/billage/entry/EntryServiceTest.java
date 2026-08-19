package com.billage.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.entry.dto.EntryDetailResponse;
import com.billage.folder.FolderService;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.ledger.dto.LedgerDetailResponse;
import com.billage.membership.GroupMembershipService;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 내역 등록 시 승인 상태 전환, 승인 처리, 승인분만 잔액에 반영되는 규칙 검증.
 */
class EntryServiceTest extends IntegrationTest {

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
	EntryRepository entryRepository;
	@Autowired
	UserRepository userRepository;

	private Long ownerId;
	private Long adminId;
	private Long outsiderId;
	private Long groupId;
	private Long ledgerId;

	@BeforeEach
	void setUp() {
		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "encoded", "일반관리자")).getId();
		outsiderId = userRepository.save(User.create("outsider@example.com", "encoded", "남의모임")).getId();

		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null)).groupId();
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(adminId, code);

		Long folderId = folderService.create(groupId, ownerId,
				new FolderCreateRequest("2026년 상반기", null)).folderId();
		ledgerId = ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("운영 장부", 3_000_000L)).ledgerId();
	}

	// --- 승인 상태 전환 ---

	@Test
	void 총무가_등록한_내역은_즉시_승인된다() {
		Long entryId = createExpense(ownerId, "공연장 대관료", 500_000L);

		Entry entry = entryRepository.findById(entryId).orElseThrow();
		assertThat(entry.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
		assertThat(entry.getApprovedByUserId()).isEqualTo(ownerId);
		assertThat(entry.getApprovedAt()).isNotNull();
	}

	@Test
	void 일반_관리자가_등록한_내역은_승인_대기다() {
		Long entryId = createExpense(adminId, "간식비", 30_000L);

		Entry entry = entryRepository.findById(entryId).orElseThrow();
		assertThat(entry.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
		assertThat(entry.getApprovedByUserId()).isNull();
		assertThat(entry.getApprovedAt()).isNull();
	}

	@Test
	void 작성자_이름은_등록_시점_값으로_보존된다() {
		Long entryId = createExpense(adminId, "간식비", 30_000L);

		EntryDetailResponse detail = entryService.getDetail(entryId, ownerId);
		assertThat(detail.createdBy().userId()).isEqualTo(adminId);
		assertThat(detail.createdBy().name()).isEqualTo("일반관리자");
	}

	// --- 승인 ---

	@Test
	void 총무는_승인_대기_내역을_승인할_수_있다() {
		Long entryId = createExpense(adminId, "간식비", 30_000L);

		entryService.approve(entryId, ownerId);

		Entry entry = entryRepository.findById(entryId).orElseThrow();
		assertThat(entry.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
		assertThat(entry.getApprovedByUserId()).isEqualTo(ownerId);
		assertThat(entry.getApprovedByName()).isEqualTo("총무");
	}

	@Test
	void 일반_관리자는_승인할_수_없다() {
		Long entryId = createExpense(adminId, "간식비", 30_000L);

		assertThatThrownBy(() -> entryService.approve(entryId, adminId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 이미_승인된_내역은_다시_승인할_수_없다() {
		Long entryId = createExpense(ownerId, "공연장 대관료", 500_000L);

		assertThatThrownBy(() -> entryService.approve(entryId, ownerId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ENTRY_ALREADY_APPROVED);
	}

	// --- 잔액 집계 ---

	@Test
	void 잔액과_잔여예산은_승인된_내역만_반영한다() {
		createIncome(ownerId, "회비 수입", 1_000_000L);
		createExpense(ownerId, "대관료", 400_000L);
		createExpense(adminId, "승인 대기 지출", 900_000L);

		LedgerDetailResponse ledger = ledgerService.getDetail(ledgerId, ownerId);

		assertThat(ledger.totalIncome()).isEqualTo(1_000_000L);
		assertThat(ledger.totalExpense()).isEqualTo(400_000L);
		assertThat(ledger.balance()).isEqualTo(600_000L);
		assertThat(ledger.remainingBudget()).isEqualTo(2_600_000L);
		// entryCount 는 승인 여부와 무관한 전체 내역 수다.
		assertThat(ledger.entryCount()).isEqualTo(3);
	}

	@Test
	void 승인하면_그때부터_잔액에_반영된다() {
		Long entryId = createExpense(adminId, "승인 대기 지출", 900_000L);
		assertThat(ledgerService.getDetail(ledgerId, ownerId).totalExpense()).isZero();

		entryService.approve(entryId, ownerId);

		assertThat(ledgerService.getDetail(ledgerId, ownerId).totalExpense()).isEqualTo(900_000L);
	}

	// --- 접근 제어 ---

	@Test
	void 다른_모임_사람은_내역을_등록하거나_조회할_수_없다() {
		Long entryId = createExpense(ownerId, "대관료", 500_000L);

		assertThatThrownBy(() -> entryService.getDetail(entryId, outsiderId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);

		assertThatThrownBy(() -> entryService.create(ledgerId, outsiderId,
				new EntryCreateRequest(EntryType.EXPENSE, "몰래 등록", 1_000L, LocalDate.now(), null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	// --- 목록 필터 ---

	@Test
	void 목록은_유형과_승인상태로_거를_수_있다() {
		createIncome(ownerId, "회비 수입", 1_000_000L);
		createExpense(ownerId, "대관료", 400_000L);
		createExpense(adminId, "승인 대기 지출", 900_000L);

		var expenses = entryService.getEntries(ledgerId, ownerId, EntryType.EXPENSE, null, null,
				PageRequest.of(0, 20));
		var pending = entryService.getEntries(ledgerId, ownerId, null, ApprovalStatus.PENDING, null,
				PageRequest.of(0, 20));
		var keyword = entryService.getEntries(ledgerId, ownerId, null, null, "대관",
				PageRequest.of(0, 20));

		assertThat(expenses.totalElements()).isEqualTo(2);
		assertThat(pending.totalElements()).isEqualTo(1);
		assertThat(keyword.content()).singleElement()
				.satisfies(entry -> assertThat(entry.title()).isEqualTo("대관료"));
	}

	@Test
	void 장부_삭제_시_내역도_함께_삭제된다() {
		createExpense(ownerId, "대관료", 400_000L);

		ledgerService.delete(ledgerId, ownerId);

		assertThat(entryRepository.countByLedgerId(ledgerId)).isZero();
	}

	private Long createExpense(Long userId, String title, long amount) {
		return entryService.create(ledgerId, userId,
				new EntryCreateRequest(EntryType.EXPENSE, title, amount, LocalDate.of(2026, 7, 20), null, null)).entryId();
	}

	private Long createIncome(Long userId, String title, long amount) {
		return entryService.create(ledgerId, userId,
				new EntryCreateRequest(EntryType.INCOME, title, amount, LocalDate.of(2026, 7, 20), null, null)).entryId();
	}
}
