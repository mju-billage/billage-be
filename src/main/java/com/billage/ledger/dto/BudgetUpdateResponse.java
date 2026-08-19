package com.billage.ledger.dto;

import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerStats;

public record BudgetUpdateResponse(
		Long ledgerId,
		Long budget,
		long totalExpense,
		Long remainingBudget
) {

	public static BudgetUpdateResponse of(Ledger ledger, LedgerStats stats) {
		return new BudgetUpdateResponse(ledger.getId(), ledger.getBudget(), stats.totalExpense(),
				stats.remainingBudget(ledger.getBudget()));
	}
}
