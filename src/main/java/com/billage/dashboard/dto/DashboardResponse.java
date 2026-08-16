package com.billage.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;
import com.billage.entry.EntryType;

/**
 * 대시보드 통합 응답. 총무·일반 권한 관리자에게 동일한 데이터를 내려준다.
 *
 * <p>{@code dues} 는 회비(Dues) 도메인 구현 전까지 모두 0이다.
 */
public record DashboardResponse(
		Long groupId,
		Summary summary,
		Approval approval,
		Dues dues,
		List<RecentEntry> recentEntries
) {

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
