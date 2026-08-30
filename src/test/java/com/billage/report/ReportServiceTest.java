package com.billage.report;

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
import com.billage.entry.EntryService;
import com.billage.entry.EntryType;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.folder.FolderService;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.membership.GroupMembershipService;
import com.billage.report.dto.ReportCreateRequest;
import com.billage.report.dto.ReportCreateResponse;
import com.billage.report.dto.ReportDetailResponse;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 보고서 스냅샷 규칙 검증 — 승인분·기간 필터, 생성 이후 원본 변경으로부터의 격리, 모임 권한.
 */
class ReportServiceTest extends IntegrationTest {

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
	ReportService reportService;
	@Autowired
	ReportRepository reportRepository;
	@Autowired
	UserRepository userRepository;

	private static final LocalDate START = LocalDate.of(2026, 1, 1);
	private static final LocalDate END = LocalDate.of(2026, 6, 30);

	private Long ownerId;
	private Long adminId;
	private Long outsiderId;
	private Long groupId;
	private Long ledgerId;
	private Long otherGroupLedgerId;

	@BeforeEach
	void setUp() {
		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "encoded", "일반관리자")).getId();
		outsiderId = userRepository.save(User.create("outsider@example.com", "encoded", "남의모임")).getId();

		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(adminId, code);
		ledgerId = createLedger(groupId, ownerId, "운영 장부");

		Long otherGroupId = groupService.create(outsiderId, new GroupCreateRequest("남의모임", null, null)).groupId();
		otherGroupLedgerId = createLedger(otherGroupId, outsiderId, "남의 장부");
	}

	// --- 스냅샷 집계 ---

	@Test
	void 기간별_보고서는_기간_안의_승인된_내역만_담는다() {
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 1_000_000L, LocalDate.of(2026, 3, 1));
		createEntry(ownerId, EntryType.EXPENSE, "대관료", 400_000L, LocalDate.of(2026, 5, 20));
		// 승인 대기 → 제외
		createEntry(adminId, EntryType.EXPENSE, "승인 대기 지출", 900_000L, LocalDate.of(2026, 5, 21));
		// 기간 밖 → 제외
		createEntry(ownerId, EntryType.EXPENSE, "하반기 지출", 700_000L, LocalDate.of(2026, 7, 1));

		ReportCreateResponse report = createPeriodReport("2026년 상반기 결산");

		assertThat(report.reportType()).isEqualTo(ReportType.BY_PERIOD);
		assertThat(report.summary().totalIncome()).isEqualTo(1_000_000L);
		assertThat(report.summary().totalExpense()).isEqualTo(400_000L);
		assertThat(report.summary().balance()).isEqualTo(600_000L);
		assertThat(report.summary().entryCount()).isEqualTo(2);
		assertThat(report.ledgers()).singleElement()
				.satisfies(ledger -> assertThat(ledger.balance()).isEqualTo(600_000L));
	}

	@Test
	void 기간에_승인된_내역이_없으면_보고서를_만들_수_없다() {
		createEntry(adminId, EntryType.EXPENSE, "승인 대기 지출", 900_000L, LocalDate.of(2026, 5, 21));

		assertThatThrownBy(() -> createReport("빈 보고서", List.of(ledgerId)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.REPORT_RANGE_EMPTY);
	}

	@Test
	void 여러_장부를_합산하고_기간에_내역이_없는_장부도_0원으로_포함한다() {
		Long emptyLedgerId = createLedger(groupId, ownerId, "빈 장부");
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 1_000_000L, LocalDate.of(2026, 3, 1));

		ReportCreateResponse report = createReport("상반기", List.of(ledgerId, emptyLedgerId));

		assertThat(report.ledgers()).hasSize(2);
		assertThat(report.summary().totalIncome()).isEqualTo(1_000_000L);
		assertThat(report.ledgers().get(1).totalIncome()).isZero();
	}

	// --- 스냅샷 격리 ---

	@Test
	void 원본_장부가_삭제되어도_보고서_내용은_그대로다() {
		createEntry(ownerId, EntryType.EXPENSE, "대관료", 400_000L, LocalDate.of(2026, 5, 20));
		Long reportId = createReport("상반기", List.of(ledgerId)).reportId();

		ledgerService.delete(ledgerId, ownerId);

		ReportDetailResponse detail = reportService.getDetail(reportId, ownerId);
		assertThat(detail.summary().totalExpense()).isEqualTo(400_000L);
		assertThat(detail.ledgers()).singleElement().satisfies(ledger -> {
			assertThat(ledger.ledgerName()).isEqualTo("운영 장부");
			assertThat(ledger.entries()).singleElement()
					.satisfies(entry -> assertThat(entry.title()).isEqualTo("대관료"));
		});
	}

	@Test
	void 모임을_삭제하면_보고서도_함께_삭제된다() {
		createEntry(ownerId, EntryType.EXPENSE, "대관료", 400_000L, LocalDate.of(2026, 5, 20));
		createReport("상반기", List.of(ledgerId));

		groupService.delete(groupId, ownerId);

		assertThat(reportRepository.findAllByGroupId(groupId)).isEmpty();
	}

	// --- 권한·소유권 ---

	@Test
	void 일반_관리자는_보고서를_만들_수_없지만_조회는_할_수_있다() {
		createEntry(ownerId, EntryType.EXPENSE, "대관료", 400_000L, LocalDate.of(2026, 5, 20));

		assertThatThrownBy(() -> reportService.create(groupId, adminId,
				new ReportCreateRequest(ReportType.BY_LEDGER, "상반기", List.of(ledgerId), null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);

		Long reportId = createReport("상반기", List.of(ledgerId)).reportId();
		assertThat(reportService.getDetail(reportId, adminId).reportId()).isEqualTo(reportId);
		assertThat(reportService.getReports(groupId, adminId, null, null, PageRequest.of(0, 20)).totalElements()).isEqualTo(1);
	}

	@Test
	void 다른_모임_사람은_보고서를_조회할_수_없다() {
		createEntry(ownerId, EntryType.EXPENSE, "대관료", 400_000L, LocalDate.of(2026, 5, 20));
		Long reportId = createReport("상반기", List.of(ledgerId)).reportId();

		assertThatThrownBy(() -> reportService.getDetail(reportId, outsiderId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);

		assertThatThrownBy(() -> reportService.getReports(groupId, outsiderId, null, null, PageRequest.of(0, 20)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 다른_모임의_장부는_보고서에_넣을_수_없다() {
		createEntry(ownerId, EntryType.EXPENSE, "대관료", 400_000L, LocalDate.of(2026, 5, 20));

		assertThatThrownBy(() -> createReport("상반기", List.of(ledgerId, otherGroupLedgerId)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.GROUP_MISMATCH);
	}

	@Test
	void 시작일이_종료일보다_늦으면_생성할_수_없다() {
		assertThatThrownBy(() -> reportService.create(groupId, ownerId,
				new ReportCreateRequest(ReportType.BY_PERIOD, "거꾸로", null, null, END, START)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	// --- 유형별 생성 규칙 ---

	@Test
	void 장부별_보고서는_기간을_받지_않고_담긴_내역의_실제_범위를_기간으로_삼는다() {
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 1_000_000L, LocalDate.of(2026, 3, 1));
		createEntry(ownerId, EntryType.EXPENSE, "하반기 지출", 700_000L, LocalDate.of(2026, 9, 15));

		ReportCreateResponse report = createReport("운영 장부 결산", List.of(ledgerId));

		assertThat(report.reportType()).isEqualTo(ReportType.BY_LEDGER);
		// 기간을 준 적이 없지만 상세 화면이 "기간 YYYY.MM.DD - YYYY.MM.DD" 를 보여 줘야 한다.
		assertThat(report.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(report.endDate()).isEqualTo(LocalDate.of(2026, 9, 15));
		assertThat(report.summary().entryCount()).isEqualTo(2);
	}

	@Test
	void 장부별_보고서는_구분으로_수입만_또는_지출만_담을_수_있다() {
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 1_000_000L, LocalDate.of(2026, 3, 1));
		createEntry(ownerId, EntryType.EXPENSE, "대관료", 400_000L, LocalDate.of(2026, 5, 20));

		ReportCreateResponse incomeOnly = reportService.create(groupId, ownerId, new ReportCreateRequest(
				ReportType.BY_LEDGER, "수입만", List.of(ledgerId), EntryType.INCOME, null, null));

		assertThat(incomeOnly.summary().totalIncome()).isEqualTo(1_000_000L);
		assertThat(incomeOnly.summary().totalExpense()).isZero();
		assertThat(incomeOnly.summary().entryCount()).isEqualTo(1);
	}

	@Test
	void 장부별_보고서에_장부를_고르지_않으면_거부한다() {
		assertThatThrownBy(() -> reportService.create(groupId, ownerId,
				new ReportCreateRequest(ReportType.BY_LEDGER, "장부 없음", List.of(), null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	@Test
	void 기간별_보고서는_그_기간에_내역이_있는_장부를_서버가_모은다() {
		Long otherLedgerId = createLedger(groupId, ownerId, "MT 장부");
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 1_000_000L, LocalDate.of(2026, 3, 1));
		entryService.create(otherLedgerId, ownerId, new EntryCreateRequest(EntryType.EXPENSE, "숙소비", 300_000L,
				LocalDate.of(2026, 4, 1), null, null));
		// 기간 밖에만 내역이 있는 장부는 포함되지 않는다.
		Long outOfRangeLedgerId = createLedger(groupId, ownerId, "하반기 장부");
		entryService.create(outOfRangeLedgerId, ownerId, new EntryCreateRequest(EntryType.EXPENSE, "하반기", 100_000L,
				LocalDate.of(2026, 9, 1), null, null));

		ReportCreateResponse report = createPeriodReport("상반기 결산");

		assertThat(report.ledgers()).hasSize(2);
		assertThat(report.ledgers()).extracting(ReportCreateResponse.LedgerSummary::ledgerName)
				.containsExactlyInAnyOrder("운영 장부", "MT 장부");
	}

	@Test
	void 기간별_보고서는_기간_직전까지의_누적_잔액에서_출발하는_잔액_흐름을_담는다() {
		// 기간 전에 이미 쌓여 있던 잔액.
		createEntry(ownerId, EntryType.INCOME, "이월금", 500_000L, LocalDate.of(2025, 12, 20));
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 1_000_000L, LocalDate.of(2026, 3, 1));
		createEntry(ownerId, EntryType.EXPENSE, "대관료", 400_000L, LocalDate.of(2026, 5, 20));

		ReportCreateResponse report = createPeriodReport("상반기 결산");

		assertThat(report.summary().openingBalance()).isEqualTo(500_000L);
		assertThat(report.summary().closingBalance()).isEqualTo(1_100_000L);
	}

	@Test
	void 장부별_보고서에는_잔액_흐름이_없다() {
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 1_000_000L, LocalDate.of(2026, 3, 1));

		ReportCreateResponse report = createReport("운영 장부 결산", List.of(ledgerId));

		assertThat(report.summary().openingBalance()).isNull();
		assertThat(report.summary().closingBalance()).isNull();
	}

	@Test
	void 목록은_유형_탭과_제목_검색으로_거를_수_있다() {
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 1_000_000L, LocalDate.of(2026, 3, 1));
		createReport("장부별 결산", List.of(ledgerId));
		createPeriodReport("기간별 결산");

		var byLedger = reportService.getReports(groupId, ownerId, ReportType.BY_LEDGER, null,
				PageRequest.of(0, 20));
		var searched = reportService.getReports(groupId, ownerId, null, "기간별", PageRequest.of(0, 20));

		assertThat(byLedger.content()).singleElement()
				.satisfies(report -> assertThat(report.title()).isEqualTo("장부별 결산"));
		assertThat(searched.content()).singleElement()
				.satisfies(report -> assertThat(report.title()).isEqualTo("기간별 결산"));
	}

	private Long createLedger(Long groupId, Long userId, String name) {
		Long folderId = folderService.create(groupId, userId, new FolderCreateRequest(name + " 폴더", null))
				.folderId();
		return ledgerService.create(folderId, userId, new LedgerCreateRequest(name, null)).ledgerId();
	}

	private void createEntry(Long userId, EntryType type, String title, long amount, LocalDate occurredOn) {
		entryService.create(ledgerId, userId,
				new EntryCreateRequest(type, title, amount, occurredOn, null, null));
	}

	/** 장부별 보고서. 기간을 받지 않으며 담긴 내역의 실제 범위가 기간이 된다. */
	private ReportCreateResponse createReport(String title, List<Long> ledgerIds) {
		return reportService.create(groupId, ownerId,
				new ReportCreateRequest(ReportType.BY_LEDGER, title, ledgerIds, null, null, null));
	}

	/** 기간별 보고서. 장부를 받지 않으며 그 기간에 내역이 있는 장부를 서버가 모은다. */
	private ReportCreateResponse createPeriodReport(String title) {
		return reportService.create(groupId, ownerId,
				new ReportCreateRequest(ReportType.BY_PERIOD, title, null, null, START, END));
	}
}
