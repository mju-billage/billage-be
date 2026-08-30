package com.billage.ledger.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerStats;

/**
 * 모임 전체 장부 목록 항목.
 *
 * <p>폴더 하나에 갇힌 {@link LedgerSummaryResponse} 와 달리 폴더 구조를 가로질러 모으므로
 * 어느 폴더 소속인지 함께 내려 준다. 최상위 영역의 장부는 {@code folderId}·{@code folderName} 이 null 이다.
 */
public record GroupLedgerResponse(
		Long ledgerId,
		Long folderId,
		String folderName,
		String name,
		Long budget,
		long totalIncome,
		long totalExpense,
		long balance,
		Long remainingBudget,
		/** 예산 소진율(%). 예산 미설정이면 null. */
		BigDecimal budgetUsageRate,
		long entryCount,
		OffsetDateTime createdAt
) {

	public static GroupLedgerResponse of(Ledger ledger, LedgerStats stats) {
		String folderName = ledger.getFolder() == null ? null : ledger.getFolder().getName();
		return new GroupLedgerResponse(ledger.getId(), ledger.getFolderId(), folderName, ledger.getName(),
				ledger.getBudget(), stats.totalIncome(), stats.totalExpense(), stats.balance(),
				stats.remainingBudget(ledger.getBudget()), stats.budgetUsageRate(ledger.getBudget()),
				stats.entryCount(), KoreanTime.toOffset(ledger.getCreatedAt()));
	}
}
