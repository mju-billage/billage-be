package com.billage.ledger.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerStats;

public record LedgerDetailResponse(
		Long ledgerId,
		Long folderId,
		String folderName,
		String name,
		Long budget,
		long totalIncome,
		long totalExpense,
		long balance,
		Long remainingBudget,
		long entryCount,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {

	public static LedgerDetailResponse of(Ledger ledger, LedgerStats stats) {
		String folderName = ledger.getFolder() == null ? null : ledger.getFolder().getName();
		return new LedgerDetailResponse(ledger.getId(), ledger.getFolderId(), folderName, ledger.getName(),
				ledger.getBudget(), stats.totalIncome(), stats.totalExpense(), stats.balance(),
				stats.remainingBudget(ledger.getBudget()), stats.entryCount(),
				KoreanTime.toOffset(ledger.getCreatedAt()), KoreanTime.toOffset(ledger.getUpdatedAt()));
	}
}
