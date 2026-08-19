package com.billage.entry.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.billage.common.response.KoreanTime;
import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;
import com.billage.entry.EntryType;

public record EntryCreateResponse(
		Long entryId,
		Long ledgerId,
		EntryType type,
		String title,
		Long amount,
		LocalDate occurredOn,
		String memo,
		ApprovalStatus approvalStatus,
		List<Object> receiptFiles,
		OffsetDateTime createdAt
) {

	public static EntryCreateResponse from(Entry entry) {
		return new EntryCreateResponse(entry.getId(), entry.getLedger().getId(), entry.getType(), entry.getTitle(),
				entry.getAmount(), entry.getOccurredOn(), entry.getMemo(), entry.getApprovalStatus(),
				List.of(), KoreanTime.toOffset(entry.getCreatedAt()));
	}
}
