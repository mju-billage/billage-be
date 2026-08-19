package com.billage.ledger;

/**
 * 장부 집계값. 승인(APPROVED)된 내역만 반영하며 잔액은 저장하지 않고 매번 계산한다.
 *
 * <p><b>내역(Entry) 도메인 구현 전이라 현재는 항상 0을 반환한다.</b>
 * Entry 를 추가할 때 이 클래스의 생성 지점({@code LedgerService})만 실제 집계 쿼리로 바꾸면 된다.
 */
public record LedgerStats(long totalIncome, long totalExpense, long entryCount) {

	public static final LedgerStats EMPTY = new LedgerStats(0L, 0L, 0L);

	public long balance() {
		return totalIncome - totalExpense;
	}

	/** 예산이 없으면 null. 초과 지출이면 음수가 될 수 있다. */
	public Long remainingBudget(Long budget) {
		return budget == null ? null : budget - totalExpense;
	}
}
