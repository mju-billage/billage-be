package com.billage.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dashboard.dto.DashboardResponse;
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

	private void createEntry(Long ledgerId, Long userId, EntryType type, String title, long amount) {
		createEntry(ledgerId, userId, type, title, amount, LocalDate.of(2026, 7, 20));
	}

	private void createEntry(Long ledgerId, Long userId, EntryType type, String title, long amount,
			LocalDate occurredOn) {
		entryService.create(ledgerId, userId, new EntryCreateRequest(type, title, amount, occurredOn, null, null, null));
	}
}
