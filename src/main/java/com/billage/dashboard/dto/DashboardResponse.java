package com.billage.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;
import com.billage.entry.EntryType;

/**
 * 대시보드 통합 응답. 총무·일반 권한 관리자에게 동일한 데이터를 내려준다.
 *
 * <p>{@code dues} 는 진행 중(OPEN)인 회비만 집계한다 — 마감분은 이미 장부 내역에 반영돼 있다.
 */
public record DashboardResponse(
		Long groupId,
		Summary summary,
		Approval approval,
		Dues dues,
		List<RecentEntry> recentEntries,

		/** 최근 14일(오늘 포함) 일자별 수입·지출. 화면 상단 캘린더 카드가 쓴다. */
		Calendar calendar,

		/** 마감이 임박한 회비 최대 3건. 화면의 회비 현황 카드(캐러셀)가 쓴다. */
		List<UpcomingDues> upcomingDues,

		/**
		 * 안 읽은 알림 존재 여부. 상단 앱바의 종 아이콘 뱃지가 이 값을 본다.
		 *
		 * <p>알림 도메인이 아직 없어 <b>항상 false</b> 다. 화면이 뱃지 로직을 나중에 고치지 않아도 되도록
		 * 필드는 먼저 열어 둔다.
		 */
		boolean hasUnreadNotification
) {

	/**
	 * 일자별 집계. 금액이 없는 날은 {@code days} 에 담지 않는다 —
	 * 화면이 "금액 데이터가 없는 날은 금액 표기 X" 라 빈 날짜를 채워 보낼 이유가 없다.
	 */
	public record Calendar(LocalDate from, LocalDate to, List<Day> days) {

		public record Day(LocalDate date, long income, long expense) {
		}
	}

	/**
	 * 마감 임박 회비 한 건.
	 *
	 * @param daysLeft 오늘부터 마감일까지 남은 일수. 마감일이 지났으면 음수다.
	 *                 화면은 이 값으로 D-day 뱃지 색을 정한다(경과~D-3 / D-3 초과~D-7 / D-7 초과).
	 */
	public record UpcomingDues(
			Long duesId,
			String title,
			LocalDate dueDate,
			long daysLeft,
			long paidCount,
			long targetCount
	) {
	}

	/** 승인된 내역만 반영한다. 잔액은 저장하지 않고 매번 계산한다. */
	public record Summary(long totalIncome, long totalExpense, long balance, long ledgerCount) {
	}

	public record Approval(long pendingEntryCount) {
	}

	public record Dues(long activeDuesCount, long totalTargetCount, long paidCount, long unpaidCount) {

		public static final Dues EMPTY = new Dues(0L, 0L, 0L, 0L);
	}

	public record RecentEntry(
			Long entryId,
			Long ledgerId,
			String ledgerName,
			EntryType type,
			String title,
			Long amount,
			LocalDate occurredOn,
			ApprovalStatus approvalStatus
	) {

		public static RecentEntry from(Entry entry) {
			return new RecentEntry(entry.getId(), entry.getLedger().getId(), entry.getLedger().getName(),
					entry.getType(), entry.getTitle(), entry.getAmount(), entry.getOccurredOn(),
					entry.getApprovalStatus());
		}
	}
}
