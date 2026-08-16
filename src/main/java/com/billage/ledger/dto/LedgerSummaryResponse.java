package com.billage.ledger.dto;

import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerStats;

/** 장부 목록 항목. 집계값은 승인된 내역만 반영한다. */
public record LedgerSummaryResponse(
		Long ledgerId,
		Long folderId,
		String name,
		Long budget,
		long totalIncome,
		long totalExpense,
		long balance,
		Long remainingBudget,
		long entryCount
) {

	public static LedgerSummaryResponse of(Ledger ledger, LedgerStats stats) {
		return new LedgerSummaryResponse(ledger.getId(), ledger.getFolderId(), ledger.getName(), ledger.getBudget(),
				stats.totalIncome(), stats.totalExpense(), stats.balance(),
				stats.remainingBudget(ledger.getBudget()), stats.entryCount());
	}
}
