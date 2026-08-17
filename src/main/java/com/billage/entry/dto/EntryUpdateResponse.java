package com.billage.entry.dto;

import java.time.LocalDate;

import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;
import com.billage.entry.EntryType;

/** 내역 수정 응답. */
public record EntryUpdateResponse(
		Long entryId,
		EntryType type,
		String title,
		Long amount,
		LocalDate occurredOn,
		String memo,
		ApprovalStatus approvalStatus
) {

	public static EntryUpdateResponse from(Entry entry) {
		return new EntryUpdateResponse(entry.getId(), entry.getType(), entry.getTitle(), entry.getAmount(),
				entry.getOccurredOn(), entry.getMemo(), entry.getApprovalStatus());
	}
}
