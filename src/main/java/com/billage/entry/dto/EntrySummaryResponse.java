package com.billage.entry.dto;

import java.time.LocalDate;

import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;
import com.billage.entry.EntryType;

/** 내역 목록 항목. {@code receiptCount} 는 연결된 증빙 파일 수. */
public record EntrySummaryResponse(
		Long entryId,
		EntryType type,
		String title,
		Long amount,
		LocalDate occurredOn,
		ApprovalStatus approvalStatus,
		Long createdByUserId,
		String createdByName,
		long receiptCount
) {

	public static EntrySummaryResponse of(Entry entry, long receiptCount) {
		return new EntrySummaryResponse(entry.getId(), entry.getType(), entry.getTitle(), entry.getAmount(),
				entry.getOccurredOn(), entry.getApprovalStatus(), entry.getCreatedByUserId(),
				entry.getCreatedByName(), receiptCount);
	}
}
