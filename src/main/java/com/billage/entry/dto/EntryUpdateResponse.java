package com.billage.entry.dto;

import java.time.LocalDate;

import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;
import com.billage.entry.EntryType;

/** 내역 수정 응답. */
public record EntryUpdateResponse(
		Long entryId,
		Long ledgerId,
		String ledgerName,
		EntryType type,
		String title,
		Long amount,
		LocalDate occurredOn,
		String memo,
		ApprovalStatus approvalStatus
) {

	public static EntryUpdateResponse from(Entry entry) {
		return new EntryUpdateResponse(entry.getId(), entry.getLedger().getId(), entry.getLedger().getName(),
				entry.getType(), entry.getTitle(), entry.getAmount(),
				entry.getOccurredOn(), entry.getMemo(), entry.getApprovalStatus());
	}
}
