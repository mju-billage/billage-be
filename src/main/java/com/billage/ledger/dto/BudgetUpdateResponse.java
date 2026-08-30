package com.billage.ledger.dto;

import java.math.BigDecimal;

import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerStats;

public record BudgetUpdateResponse(
		Long ledgerId,
		Long budget,
		long totalExpense,
		Long remainingBudget,
		/** 예산 소진율(%). 예산 미설정이면 null. */
		BigDecimal budgetUsageRate
) {

	public static BudgetUpdateResponse of(Ledger ledger, LedgerStats stats) {
		return new BudgetUpdateResponse(ledger.getId(), ledger.getBudget(), stats.totalExpense(),
				stats.remainingBudget(ledger.getBudget()), stats.budgetUsageRate(ledger.getBudget()));
	}
}
