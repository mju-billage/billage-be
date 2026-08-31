package com.billage.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.response.KoreanTime;
import com.billage.dashboard.dto.DashboardResponse;
import com.billage.dues.DuesService;
import com.billage.dues.dto.DuesCreateRequest;
import com.billage.member.MemberService;
import com.billage.member.dto.MemberCreateRequest;
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
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 대시보드 집계 — 모임 전체 장부 기준, 승인된 내역만 잔액에 반영.
 */
class DashboardServiceTest extends IntegrationTest {

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
	DashboardService dashboardService;
	@Autowired
	DuesService duesService;
	@Autowired
	MemberService memberService;
	@Autowired
	UserRepository userRepository;

	private Long ownerId;
	private Long adminId;
	private Long outsiderId;
	private Long groupId;
	private Long firstLedgerId;
	private Long secondLedgerId;

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
		firstLedgerId = ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("운영 장부", null)).ledgerId();
		secondLedgerId = ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("공연 장부", null)).ledgerId();
	}

	@Test
	void 잔액은_모임의_모든_장부를_합산한다() {
		createEntry(firstLedgerId, ownerId, EntryType.INCOME, "회비 수입", 1_000_000L);
		createEntry(secondLedgerId, ownerId, EntryType.EXPENSE, "대관료", 400_000L);

		DashboardResponse.Summary summary = dashboardService.getDashboard(groupId, ownerId, 5).summary();

		assertThat(summary.totalIncome()).isEqualTo(1_000_000L);
		assertThat(summary.totalExpense()).isEqualTo(400_000L);
		assertThat(summary.balance()).isEqualTo(600_000L);
		assertThat(summary.ledgerCount()).isEqualTo(2);
	}

	@Test
	void 승인_대기_내역은_잔액에서_빠지고_대기_수로_집계된다() {
		createEntry(firstLedgerId, ownerId, EntryType.INCOME, "회비 수입", 1_000_000L);
		createEntry(firstLedgerId, adminId, EntryType.EXPENSE, "승인 대기 지출", 900_000L);

		DashboardResponse dashboard = dashboardService.getDashboard(groupId, ownerId, 5);

		assertThat(dashboard.summary().totalExpense()).isZero();
		assertThat(dashboard.summary().balance()).isEqualTo(1_000_000L);
		assertThat(dashboard.approval().pendingEntryCount()).isEqualTo(1);
	}

	@Test
	void 최근_내역은_발생일_최신순으로_요청한_개수만_반환한다() {
		createEntry(firstLedgerId, ownerId, EntryType.EXPENSE, "7월 지출", 10_000L, LocalDate.of(2026, 7, 1));
		createEntry(firstLedgerId, ownerId, EntryType.EXPENSE, "8월 지출", 20_000L, LocalDate.of(2026, 8, 1));
		createEntry(secondLedgerId, ownerId, EntryType.EXPENSE, "9월 지출", 30_000L, LocalDate.of(2026, 9, 1));

		var recentEntries = dashboardService.getDashboard(groupId, ownerId, 2).recentEntries();

		assertThat(recentEntries).hasSize(2);
		assertThat(recentEntries.get(0).title()).isEqualTo("9월 지출");
		assertThat(recentEntries.get(0).ledgerName()).isEqualTo("공연 장부");
		assertThat(recentEntries.get(1).title()).isEqualTo("8월 지출");
	}

	@Test
	void 일반_관리자도_같은_대시보드를_본다() {
		createEntry(firstLedgerId, adminId, EntryType.EXPENSE, "승인 대기 지출", 900_000L);

		DashboardResponse ownerView = dashboardService.getDashboard(groupId, ownerId, 5);
		DashboardResponse adminView = dashboardService.getDashboard(groupId, adminId, 5);

		assertThat(adminView).isEqualTo(ownerView);
		assertThat(adminView.approval().pendingEntryCount()).isEqualTo(1);
	}

	@Test
	void 다른_모임의_대시보드는_조회할_수_없다() {
		assertThatThrownBy(() -> dashboardService.getDashboard(groupId, outsiderId, 5))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 최근_내역_개수가_허용_범위를_벗어나면_거부된다() {
		assertThatThrownBy(() -> dashboardService.getDashboard(groupId, ownerId, 0))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_QUERY_PARAMETER);

		assertThatThrownBy(() -> dashboardService.getDashboard(groupId, ownerId,
				DashboardService.MAX_RECENT_ENTRY_SIZE + 1))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_QUERY_PARAMETER);
	}

	@Test
	void 회비_집계는_도메인_구현_전까지_0이다() {
		DashboardResponse.Dues dues = dashboardService.getDashboard(groupId, ownerId, 5).dues();

		assertThat(dues.activeDuesCount()).isZero();
		assertThat(dues.totalTargetCount()).isZero();
		assertThat(dues.paidCount()).isZero();
		assertThat(dues.unpaidCount()).isZero();
	}

	// --- 캘린더 ---

	@Test
	void 캘린더는_오늘_포함_14일을_보고_금액이_없는_날은_담지_않는다() {
		LocalDate today = LocalDate.now(KoreanTime.ZONE);
		createEntry(firstLedgerId, ownerId, EntryType.INCOME, "오늘 수입", 100_000L, today);
		createEntry(firstLedgerId, ownerId, EntryType.EXPENSE, "오늘 지출", 30_000L, today);
		createEntry(firstLedgerId, ownerId, EntryType.EXPENSE, "13일 전", 50_000L, today.minusDays(13));
		// 14일을 넘어가면 카드에 들어오지 않는다.
		createEntry(firstLedgerId, ownerId, EntryType.EXPENSE, "14일 전", 70_000L, today.minusDays(14));

		var calendar = dashboardService.getDashboard(groupId, ownerId, 5).calendar();

		assertThat(calendar.from()).isEqualTo(today.minusDays(13));
		assertThat(calendar.to()).isEqualTo(today);
		// 금액이 있는 날만 담긴다 — 사이의 빈 날짜는 아예 없다.
		assertThat(calendar.days()).hasSize(2);
		assertThat(calendar.days()).last().satisfies(day -> {
			assertThat(day.date()).isEqualTo(today);
			assertThat(day.income()).isEqualTo(100_000L);
			assertThat(day.expense()).isEqualTo(30_000L);
		});
	}

	@Test
	void 캘린더는_승인된_내역만_센다() {
		LocalDate today = LocalDate.now(KoreanTime.ZONE);
		createEntry(firstLedgerId, ownerId, EntryType.EXPENSE, "승인된 지출", 30_000L, today);
		// 일반 관리자가 올린 건은 승인 대기라 빠진다.
		createEntry(firstLedgerId, adminId, EntryType.EXPENSE, "승인 대기 지출", 900_000L, today);

		var calendar = dashboardService.getDashboard(groupId, ownerId, 5).calendar();

		assertThat(calendar.days()).singleElement()
				.satisfies(day -> assertThat(day.expense()).isEqualTo(30_000L));
	}

	@Test
	void 월간_캘린더는_그_달만_본다() {
		createEntry(firstLedgerId, ownerId, EntryType.INCOME, "7월 수입", 100_000L, LocalDate.of(2026, 7, 20));
		createEntry(firstLedgerId, ownerId, EntryType.INCOME, "8월 수입", 200_000L, LocalDate.of(2026, 8, 3));

		var july = dashboardService.getCalendar(groupId, ownerId, YearMonth.of(2026, 7));

		assertThat(july.from()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(july.to()).isEqualTo(LocalDate.of(2026, 7, 31));
		assertThat(july.days()).singleElement()
				.satisfies(day -> assertThat(day.income()).isEqualTo(100_000L));
	}

	@Test
	void 다른_모임_사람은_캘린더를_볼_수_없다() {
		assertThatThrownBy(() -> dashboardService.getCalendar(groupId, outsiderId, YearMonth.of(2026, 7)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	// --- 마감 임박 회비 카드 ---

	@Test
	void 마감이_임박한_순으로_최대_세_건만_준다() {
		Long memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, null, null)).memberId();
		LocalDate today = LocalDate.now(KoreanTime.ZONE);
		createDues("네번째", today.plusDays(40), memberId);
		createDues("첫번째", today.plusDays(3), memberId);
		createDues("두번째", today.plusDays(10), memberId);
		createDues("세번째", today.plusDays(20), memberId);

		var upcoming = dashboardService.getDashboard(groupId, ownerId, 5).upcomingDues();

		assertThat(upcoming).hasSize(3);
		assertThat(upcoming).extracting(DashboardResponse.UpcomingDues::title)
				.containsExactly("첫번째", "두번째", "세번째");
		assertThat(upcoming.get(0).daysLeft()).isEqualTo(3);
		assertThat(upcoming.get(0).targetCount()).isEqualTo(1);
		assertThat(upcoming.get(0).paidCount()).isZero();
	}

	@Test
	void 마감된_회비는_카드에_올라오지_않는다() {
		Long memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, null, null)).memberId();
		LocalDate today = LocalDate.now(KoreanTime.ZONE);
		Long duesId = createDues("마감할 회비", today.plusDays(3), memberId);
		duesService.close(duesId, ownerId);

		assertThat(dashboardService.getDashboard(groupId, ownerId, 5).upcomingDues()).isEmpty();
	}

	@Test
	void 알림_도메인이_없어_안읽음_표시는_항상_꺼져_있다() {
		assertThat(dashboardService.getDashboard(groupId, ownerId, 5).hasUnreadNotification()).isFalse();
	}

	private Long createDues(String title, LocalDate dueDate, Long memberId) {
		return duesService.create(groupId, ownerId, new DuesCreateRequest(title, 30_000L,
				LocalDate.now(KoreanTime.ZONE), dueDate, List.of(memberId), firstLedgerId)).duesId();
	}

	private void createEntry(Long ledgerId, Long userId, EntryType type, String title, long amount) {
		createEntry(ledgerId, userId, type, title, amount, LocalDate.of(2026, 7, 20));
	}

	private void createEntry(Long ledgerId, Long userId, EntryType type, String title, long amount,
			LocalDate occurredOn) {
		entryService.create(ledgerId, userId, new EntryCreateRequest(type, title, amount, occurredOn, null, null, null));
	}
}
