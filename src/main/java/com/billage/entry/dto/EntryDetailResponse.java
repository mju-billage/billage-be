package com.billage.entry.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.billage.common.response.KoreanTime;
import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;
import com.billage.entry.EntryType;
import com.billage.file.dto.ReceiptFileResponse;

/**
 * 내역 상세. 작성자·승인자 이름은 기록 시점 값이라 이후 해당 관리자가 탈퇴해도 그대로 유지된다.
 */
public record EntryDetailResponse(
		Long entryId,
		Long ledgerId,
		String ledgerName,
		EntryType type,
		String title,
		Long amount,
		LocalDate occurredOn,
		String memo,
		ApprovalStatus approvalStatus,
		Actor createdBy,
		Actor approvedBy,
		OffsetDateTime approvedAt,
		List<ReceiptFileResponse> receiptFiles
) {

	public record Actor(Long userId, String name) {
	}

	public static EntryDetailResponse of(Entry entry, List<ReceiptFileResponse> receiptFiles) {
		Actor approvedBy = entry.getApprovedByUserId() == null
				? null
				: new Actor(entry.getApprovedByUserId(), entry.getApprovedByName());

		return new EntryDetailResponse(entry.getId(), entry.getLedger().getId(), entry.getLedger().getName(),
				entry.getType(), entry.getTitle(), entry.getAmount(), entry.getOccurredOn(), entry.getMemo(),
				entry.getApprovalStatus(), new Actor(entry.getCreatedByUserId(), entry.getCreatedByName()),
				approvedBy, KoreanTime.toOffset(entry.getApprovedAt()), receiptFiles);
	}
}
