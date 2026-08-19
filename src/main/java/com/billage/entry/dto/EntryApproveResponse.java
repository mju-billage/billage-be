package com.billage.entry.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;

public record EntryApproveResponse(
		Long entryId,
		ApprovalStatus approvalStatus,
		Long approvedByUserId,
		OffsetDateTime approvedAt
) {

	public static EntryApproveResponse from(Entry entry) {
		return new EntryApproveResponse(entry.getId(), entry.getApprovalStatus(), entry.getApprovedByUserId(),
				KoreanTime.toOffset(entry.getApprovedAt()));
	}
}
