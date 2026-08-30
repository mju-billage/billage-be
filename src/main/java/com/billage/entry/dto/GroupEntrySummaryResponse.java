package com.billage.entry.dto;

import java.time.LocalDate;

import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;
import com.billage.entry.EntryType;

/**
 * 모임 전체 내역 목록 항목.
 *
 * <p>장부별 목록({@link EntrySummaryResponse})과 달리 여러 장부가 섞여 나오므로
 * 어느 장부의 내역인지 함께 내려 준다 — 화면도 내역명 왼쪽에 장부 태그를 붙인다.
 */
public record GroupEntrySummaryResponse(
		Long entryId,
		Long ledgerId,
		String ledgerName,
		EntryType type,
		String title,
		Long amount,
		LocalDate occurredOn,
		ApprovalStatus approvalStatus,
		Long createdByUserId,
		String createdByName,
		long receiptCount
) {

	public static GroupEntrySummaryResponse of(Entry entry, long receiptCount) {
		return new GroupEntrySummaryResponse(entry.getId(), entry.getLedger().getId(), entry.getLedger().getName(),
				entry.getType(), entry.getTitle(), entry.getAmount(), entry.getOccurredOn(),
				entry.getApprovalStatus(), entry.getCreatedByUserId(), entry.getCreatedByName(), receiptCount);
	}
}
