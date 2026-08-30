package com.billage.ledger.dto;

import java.math.BigDecimal;

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
		/** 예산 소진율(%). 예산 미설정이면 null. */
		BigDecimal budgetUsageRate,
		long entryCount
) {

	public static LedgerSummaryResponse of(Ledger ledger, LedgerStats stats) {
		return new LedgerSummaryResponse(ledger.getId(), ledger.getFolderId(), ledger.getName(), ledger.getBudget(),
				stats.totalIncome(), stats.totalExpense(), stats.balance(),
				stats.remainingBudget(ledger.getBudget()), stats.budgetUsageRate(ledger.getBudget()),
				stats.entryCount());
	}
}
