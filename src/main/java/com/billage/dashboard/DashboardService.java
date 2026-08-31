package com.billage.dashboard;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dashboard.dto.DashboardResponse;
import com.billage.common.response.KoreanTime;
import com.billage.dues.Dues;
import com.billage.dues.DuesMemberRepository;
import com.billage.dues.DuesRepository;
import com.billage.dues.DuesStatus;
import com.billage.dues.PaymentStatus;
import com.billage.entry.ApprovalStatus;
import com.billage.entry.EntryRepository;
import com.billage.entry.EntryType;
import com.billage.ledger.LedgerRepository;
import com.billage.membership.GroupAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * 대시보드 화면용 통합 조회. 집계 테이블 없이 SQL 집계만 사용한다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

	static final int MIN_RECENT_ENTRY_SIZE = 1;
	static final int MAX_RECENT_ENTRY_SIZE = 20;

	/** 캘린더 카드가 보여 주는 기간. 화면명세 "당일 기준으로 이전 날짜 13일자 표기 (당일포함 14일)". */
	private static final int CALENDAR_DAYS = 14;

	/** 회비 현황 카드에 올리는 건수. 화면명세 "가장 마감이 임박한 3개". */
	private static final int UPCOMING_DUES_SIZE = 3;

	private final EntryRepository entryRepository;
	private final DuesRepository duesRepository;
	private final DuesMemberRepository duesMemberRepository;
	private final LedgerRepository ledgerRepository;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public DashboardResponse getDashboard(Long groupId, Long userId, int recentEntrySize) {
		guard.requireMembership(groupId, userId);
		validateRecentEntrySize(recentEntrySize);

		Map<EntryType, Long> approvedSums = entryRepository.sumApprovedByTypeForGroup(groupId);
		long totalIncome = approvedSums.getOrDefault(EntryType.INCOME, 0L);
		long totalExpense = approvedSums.getOrDefault(EntryType.EXPENSE, 0L);

		DashboardResponse.Summary summary = new DashboardResponse.Summary(
				totalIncome, totalExpense, totalIncome - totalExpense,
				ledgerRepository.countByGroupId(groupId));

		DashboardResponse.Approval approval = new DashboardResponse.Approval(
				entryRepository.countByGroupIdAndStatus(groupId, ApprovalStatus.PENDING));

		List<DashboardResponse.RecentEntry> recentEntries =
				entryRepository.findRecentByGroupId(groupId, PageRequest.of(0, recentEntrySize)).stream()
						.map(DashboardResponse.RecentEntry::from)
						.toList();

		// 진행 중(OPEN)인 회비만 집계한다 — 마감된 회비는 이미 장부 내역으로 반영돼 있다.
		Map<PaymentStatus, Long> duesCounts = duesMemberRepository.countOpenTargetsByGroup(groupId);
		long paid = duesCounts.getOrDefault(PaymentStatus.PAID, 0L);
		long unpaid = duesCounts.getOrDefault(PaymentStatus.UNPAID, 0L);
		DashboardResponse.Dues dues = new DashboardResponse.Dues(
				duesRepository.countByGroupIdAndStatus(groupId, DuesStatus.OPEN),
				paid + unpaid, paid, unpaid);

		LocalDate today = LocalDate.now(KoreanTime.ZONE);
		DashboardResponse.Calendar calendar = calendarOf(groupId, today.minusDays(CALENDAR_DAYS - 1), today);

		return new DashboardResponse(groupId, summary, approval, dues, recentEntries, calendar,
				upcomingDuesOf(groupId, today), false);
	}

	/**
	 * 「캘린더 전체보기」(월간). 대시보드 카드와 같은 형식이며 기간만 한 달이다.
	 */
	@Transactional(readOnly = true)
	public DashboardResponse.Calendar getCalendar(Long groupId, Long userId, YearMonth yearMonth) {
		guard.requireMembership(groupId, userId);

		return calendarOf(groupId, yearMonth.atDay(1), yearMonth.atEndOfMonth());
	}

	private DashboardResponse.Calendar calendarOf(Long groupId, LocalDate from, LocalDate to) {
		List<DashboardResponse.Calendar.Day> days =
				entryRepository.sumApprovedByDay(groupId, from, to).entrySet().stream()
						.map(entry -> new DashboardResponse.Calendar.Day(entry.getKey(),
								entry.getValue().getOrDefault(EntryType.INCOME, 0L),
								entry.getValue().getOrDefault(EntryType.EXPENSE, 0L)))
						.toList();

		return new DashboardResponse.Calendar(from, to, days);
	}

	/**
	 * 마감 임박 회비 카드. 대상 인원은 회비마다 세지 않고 한 번에 모아 읽는다(N+1 방지).
	 */
	private List<DashboardResponse.UpcomingDues> upcomingDuesOf(Long groupId, LocalDate today) {
		List<Dues> upcoming = duesRepository.findUpcoming(groupId, PageRequest.of(0, UPCOMING_DUES_SIZE));
		if (upcoming.isEmpty()) {
			return List.of();
		}

		Map<Long, Map<PaymentStatus, Long>> counts = duesMemberRepository.countByDues(
				upcoming.stream().map(Dues::getId).toList());

		return upcoming.stream()
				.map(dues -> {
					Map<PaymentStatus, Long> byStatus = counts.getOrDefault(dues.getId(), Map.of());
					long paid = byStatus.getOrDefault(PaymentStatus.PAID, 0L);
					long target = paid + byStatus.getOrDefault(PaymentStatus.UNPAID, 0L);
					return new DashboardResponse.UpcomingDues(dues.getId(), dues.getTitle(), dues.getDueDate(),
							ChronoUnit.DAYS.between(today, dues.getDueDate()), paid, target);
				})
				.toList();
	}

	private void validateRecentEntrySize(int size) {
		if (size < MIN_RECENT_ENTRY_SIZE || size > MAX_RECENT_ENTRY_SIZE) {
			throw new BusinessException(ErrorCode.INVALID_QUERY_PARAMETER,
					"최근 내역 개수는 %d~%d 사이여야 합니다.".formatted(MIN_RECENT_ENTRY_SIZE, MAX_RECENT_ENTRY_SIZE));
		}
	}
}
