package com.billage.ledger.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.ledger.Ledger;

public record LedgerCreateResponse(
		Long ledgerId,
		Long folderId,
		String name,
		Long budget,
		long totalIncome,
		long totalExpense,
		long balance,
		OffsetDateTime createdAt
) {

	public static LedgerCreateResponse from(Ledger ledger) {
		return new LedgerCreateResponse(ledger.getId(), ledger.getFolderId(), ledger.getName(), ledger.getBudget(),
				0L, 0L, 0L, KoreanTime.toOffset(ledger.getCreatedAt()));
	}
}
