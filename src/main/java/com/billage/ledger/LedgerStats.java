package com.billage.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 장부 집계값. 승인(APPROVED)된 내역만 반영하며 잔액은 저장하지 않고 매번 계산한다. */
public record LedgerStats(long totalIncome, long totalExpense, long entryCount) {

	public long balance() {
		return totalIncome - totalExpense;
	}

	/** 예산이 없으면 null. 초과 지출이면 음수가 될 수 있다. */
	public Long remainingBudget(Long budget) {
		return budget == null ? null : budget - totalExpense;
	}

	/**
	 * 예산 소진율(%). 화면의 프로그레스 바와 「통계/분석」의 정렬 기준이라 서버가 계산해 내려 준다.
	 *
	 * <p>예산이 없거나 0원이면 {@code null} 이다 — 0으로 나눌 수 없고, 화면도 이때는 바를 그리지 않는다.
	 * 예산을 넘겨 쓰면 100을 넘는 값이 나온다.
	 */
	public BigDecimal budgetUsageRate(Long budget) {
		if (budget == null || budget == 0L) {
			return null;
		}
		return BigDecimal.valueOf(totalExpense)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(budget), 2, RoundingMode.HALF_UP);
	}
}
