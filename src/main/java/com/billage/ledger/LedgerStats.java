package com.billage.ledger;

/** 장부 집계값. 승인(APPROVED)된 내역만 반영하며 잔액은 저장하지 않고 매번 계산한다. */
public record LedgerStats(long totalIncome, long totalExpense, long entryCount) {

	public long balance() {
		return totalIncome - totalExpense;
	}

	/** 예산이 없으면 null. 초과 지출이면 음수가 될 수 있다. */
	public Long remainingBudget(Long budget) {
		return budget == null ? null : budget - totalExpense;
	}
}
