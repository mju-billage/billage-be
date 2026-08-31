package com.billage.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.entry.dto.EntryDetailResponse;
import com.billage.entry.dto.EntryUpdateRequest;
import com.billage.entry.dto.GroupEntryListResponse;
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
 * 내역 등록 시 승인 상태 전환, 승인 처리, 승인분만 잔액에 반영되는 규칙, 수정·삭제 권한(총무 전용) 검증.
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

		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
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

	@Test
	void 이름_상한_10자_사용자도_내역을_등록할_수_있다() {
		// users.name 과 entry.created_by_name 이 모두 10자로 정렬되어 있어야 한다.
		// 예전에는 가입이 100자까지 허용돼 10자 넘는 이름이면 내역 저장이 깨졌다.
		Long longNameUserId = userRepository.save(
				User.create("long@example.com", "encoded", "가나다라마바사아자차")).getId();
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(longNameUserId, code);

		Long entryId = entryService.create(ledgerId, longNameUserId,
				new EntryCreateRequest(EntryType.EXPENSE, "간식비", 30_000L,
						LocalDate.of(2026, 7, 20), null, null, null)).entryId();

		assertThat(entryRepository.findById(entryId).orElseThrow().getCreatedByName())
				.isEqualTo("가나다라마바사아자차");
	}

	// --- 수정·삭제 (총무 전용) ---

	@Test
	void 총무가_승인_완료_내역을_수정해도_승인_상태는_유지된다() {
		Long entryId = createExpense(ownerId, "대관료", 500_000L);

		entryService.update(entryId, ownerId, new EntryUpdateRequest("대관료 정정", 520_000L,
				LocalDate.of(2026, 7, 21), "금액 정정", null, null));

		Entry entry = entryRepository.findById(entryId).orElseThrow();
		assertThat(entry.getTitle()).isEqualTo("대관료 정정");
		assertThat(entry.getAmount()).isEqualTo(520_000L);
		assertThat(entry.getOccurredOn()).isEqualTo(LocalDate.of(2026, 7, 21));
		assertThat(entry.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
	}

	@Test
	void 전달하지_않은_필드는_수정되지_않는다() {
		Long entryId = createExpense(ownerId, "대관료", 500_000L);

		entryService.update(entryId, ownerId, new EntryUpdateRequest(null, 520_000L, null, null, null, null));

		Entry entry = entryRepository.findById(entryId).orElseThrow();
		assertThat(entry.getAmount()).isEqualTo(520_000L);
		assertThat(entry.getTitle()).isEqualTo("대관료");
		assertThat(entry.getOccurredOn()).isEqualTo(LocalDate.of(2026, 7, 20));
	}

	@Test
	void 내역명을_공백만으로_수정할_수_없다() {
		Long entryId = createExpense(ownerId, "대관료", 500_000L);

		assertThatThrownBy(() -> entryService.update(entryId, ownerId,
				new EntryUpdateRequest("   ", null, null, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);

		assertThat(entryRepository.findById(entryId).orElseThrow().getTitle()).isEqualTo("대관료");
	}

	@Test
	void 일반_관리자는_본인이_등록한_승인_대기_내역도_수정_삭제할_수_없다() {
		Long entryId = createExpense(adminId, "간식비", 30_000L);

		assertThatThrownBy(() -> entryService.update(entryId, adminId,
				new EntryUpdateRequest("간식비 정정", null, null, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);

		assertThatThrownBy(() -> entryService.delete(entryId, adminId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 총무가_삭제하면_잔액에서도_빠진다() {
		createIncome(ownerId, "회비 수입", 1_000_000L);
		Long entryId = createExpense(ownerId, "대관료", 400_000L);

		entryService.delete(entryId, ownerId);

		assertThat(entryRepository.findById(entryId)).isEmpty();
		LedgerDetailResponse ledger = ledgerService.getDetail(ledgerId, ownerId);
		assertThat(ledger.totalExpense()).isZero();
		assertThat(ledger.balance()).isEqualTo(1_000_000L);
	}

	@Test
	void 다른_모임_사람은_내역을_수정하거나_삭제할_수_없다() {
		Long entryId = createExpense(ownerId, "대관료", 500_000L);

		assertThatThrownBy(() -> entryService.update(entryId, outsiderId,
				new EntryUpdateRequest("몰래 수정", null, null, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);

		assertThatThrownBy(() -> entryService.delete(entryId, outsiderId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
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
				new EntryCreateRequest(EntryType.EXPENSE, "몰래 등록", 1_000L, LocalDate.now(), null, null, null)))
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

	// --- 담당자 ---

	@Test
	void 담당자를_지정하지_않으면_등록자_본인이_된다() {
		Long entryId = createExpense(ownerId, "공연장 대관료", 500_000L);

		var detail = entryService.getDetail(entryId, ownerId);
		assertThat(detail.manager().userId()).isEqualTo(ownerId);
		assertThat(detail.manager().name()).isEqualTo("총무");
	}

	@Test
	void 담당자는_등록자와_다를_수_있고_수정으로_바꿀_수_있다() {
		Long entryId = entryService.create(ledgerId, ownerId,
				new EntryCreateRequest(EntryType.EXPENSE, "간식비", 30_000L, LocalDate.of(2026, 7, 20), null,
						adminId, null)).entryId();

		assertThat(entryService.getDetail(entryId, ownerId).manager().userId()).isEqualTo(adminId);
		// 작성자는 그대로다 — 누가 입력했나의 기록이라 바뀌지 않는다.
		assertThat(entryService.getDetail(entryId, ownerId).createdBy().userId()).isEqualTo(ownerId);

		entryService.update(entryId, ownerId,
				new EntryUpdateRequest(null, null, null, null, ownerId, null));

		assertThat(entryService.getDetail(entryId, ownerId).manager().userId()).isEqualTo(ownerId);
	}

	@Test
	void 이_모임의_관리자가_아닌_사람은_담당자가_될_수_없다() {
		assertThatThrownBy(() -> entryService.create(ledgerId, ownerId,
				new EntryCreateRequest(EntryType.EXPENSE, "간식비", 30_000L, LocalDate.of(2026, 7, 20), null,
						outsiderId, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	// --- 모임 전체 내역 목록 (GNB 「내역」 탭) ---

	@Test
	void 모임_전체_목록은_장부를_넘어서_모으고_승인분으로_잔액을_낸다() {
		Long otherLedgerId = otherLedger("MT");
		createExpense(ownerId, "공연장 대관료", 500_000L);
		createIncome(ownerId, "후원금", 1_200_000L);
		entryService.create(otherLedgerId, ownerId,
				new EntryCreateRequest(EntryType.EXPENSE, "숙소비", 300_000L, LocalDate.of(2026, 7, 20), null, null, null));
		// 일반 관리자가 올린 건은 승인 대기라 잔액에 들어가지 않는다.
		entryService.create(ledgerId, adminId,
				new EntryCreateRequest(EntryType.EXPENSE, "미승인 지출", 900_000L, LocalDate.of(2026, 7, 20), null, null,
						null));

		var result = list(null, null, null, null, null, null);

		assertThat(result.entries().totalElements()).isEqualTo(4);
		assertThat(result.summary().totalIncome()).isEqualTo(1_200_000L);
		assertThat(result.summary().totalExpense()).isEqualTo(800_000L);
		assertThat(result.summary().balance()).isEqualTo(400_000L);
	}

	@Test
	void 장부를_여러_개_골라_거를_수_있다() {
		Long mtLedgerId = otherLedger("MT");
		Long etcLedgerId = otherLedger("비품");
		createExpense(ownerId, "공연장 대관료", 500_000L);
		entryService.create(mtLedgerId, ownerId,
				new EntryCreateRequest(EntryType.EXPENSE, "숙소비", 300_000L, LocalDate.of(2026, 7, 20), null, null, null));
		entryService.create(etcLedgerId, ownerId,
				new EntryCreateRequest(EntryType.EXPENSE, "프린터", 100_000L, LocalDate.of(2026, 7, 20), null, null, null));

		var result = list(List.of(ledgerId, mtLedgerId), null, null, null, null, null);

		assertThat(result.entries().totalElements()).isEqualTo(2);
		// 잔액도 고른 장부만 합산한다.
		assertThat(result.summary().totalExpense()).isEqualTo(800_000L);
	}

	@Test
	void 검색어는_내역명뿐_아니라_장부명에도_걸린다() {
		Long mtLedgerId = otherLedger("MT");
		createExpense(ownerId, "공연장 대관료", 500_000L);
		entryService.create(mtLedgerId, ownerId,
				new EntryCreateRequest(EntryType.EXPENSE, "숙소비", 300_000L, LocalDate.of(2026, 7, 20), null, null, null));

		// '숙소비'라는 내역명에는 없지만 장부명이 MT 라서 걸린다.
		var byLedgerName = list(null, null, null, null, null, "MT");
		var byTitle = list(null, null, null, null, null, "대관");

		assertThat(byLedgerName.entries().content()).singleElement()
				.satisfies(entry -> assertThat(entry.ledgerName()).isEqualTo("MT"));
		assertThat(byTitle.entries().content()).singleElement()
				.satisfies(entry -> assertThat(entry.title()).isEqualTo("공연장 대관료"));
	}

	@Test
	void 기간과_구분으로_거르면_잔액도_같은_조건을_따른다() {
		entryService.create(ledgerId, ownerId,
				new EntryCreateRequest(EntryType.INCOME, "6월 후원금", 100_000L, LocalDate.of(2026, 6, 10), null, null, null));
		entryService.create(ledgerId, ownerId,
				new EntryCreateRequest(EntryType.INCOME, "7월 후원금", 200_000L, LocalDate.of(2026, 7, 20), null, null, null));
		createExpense(ownerId, "공연장 대관료", 500_000L);

		var julyIncome = list(null, EntryType.INCOME, null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null);

		assertThat(julyIncome.entries().totalElements()).isEqualTo(1);
		assertThat(julyIncome.summary().totalIncome()).isEqualTo(200_000L);
		assertThat(julyIncome.summary().totalExpense()).isZero();
	}

	@Test
	void 승인_요청_탭은_승인_대기_내역만_보여_준다() {
		createExpense(ownerId, "총무가 올린 지출", 500_000L);
		entryService.create(ledgerId, adminId,
				new EntryCreateRequest(EntryType.EXPENSE, "승인 요청 지출", 100_000L, LocalDate.of(2026, 7, 20), null, null,
						null));

		var pending = list(null, null, ApprovalStatus.PENDING, null, null, null);

		assertThat(pending.entries().content()).singleElement()
				.satisfies(entry -> assertThat(entry.title()).isEqualTo("승인 요청 지출"));
		// 탭을 걸러도 잔액 카드는 승인분 기준을 유지한다.
		assertThat(pending.summary().totalExpense()).isEqualTo(500_000L);
	}

	@Test
	void 조회_기간이_거꾸로면_거부한다() {
		assertThatThrownBy(() -> list(null, null, null, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1), null))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_QUERY_PARAMETER);
	}

	@Test
	void 다른_모임_사람은_모임_전체_내역을_볼_수_없다() {
		assertThatThrownBy(() -> entryService.getGroupEntries(groupId, outsiderId, null, null, null, null, null, null,
				PageRequest.of(0, 20)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	// --- 회비 연결 ---

	@Test
	void 일반_내역은_회비_정보가_비어_있다() {
		Long entryId = createExpense(ownerId, "공연장 대관료", 500_000L);

		var detail = entryService.getDetail(entryId, ownerId);
		assertThat(detail.duesId()).isNull();
		assertThat(detail.duesExists()).isFalse();
		assertThat(detail.payerCount()).isZero();
		assertThat(detail.payers()).isEmpty();
	}

	private GroupEntryListResponse list(List<Long> ledgerIds, EntryType type, ApprovalStatus status,
			LocalDate from, LocalDate to, String keyword) {
		return entryService.getGroupEntries(groupId, ownerId, ledgerIds, type, status, from, to, keyword,
				PageRequest.of(0, 20));
	}

	private Long otherLedger(String name) {
		Long folderId = folderService.create(groupId, ownerId, new FolderCreateRequest(name + " 폴더", null)).folderId();
		return ledgerService.create(folderId, ownerId, new LedgerCreateRequest(name, null)).ledgerId();
	}

	private Long createExpense(Long userId, String title, long amount) {
		return entryService.create(ledgerId, userId,
				new EntryCreateRequest(EntryType.EXPENSE, title, amount, LocalDate.of(2026, 7, 20), null, null, null)).entryId();
	}

	private Long createIncome(Long userId, String title, long amount) {
		return entryService.create(ledgerId, userId,
				new EntryCreateRequest(EntryType.INCOME, title, amount, LocalDate.of(2026, 7, 20), null, null, null)).entryId();
	}
}
