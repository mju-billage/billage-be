package com.billage.dues.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.dues.Dues;
import com.billage.dues.DuesStatus;

/** 회비 생성 응답. */
public record DuesCreateResponse(
		Long duesId,
		Long groupId,
		String title,
		Long amount,
		LocalDate dueDate,
		DuesStatus status,
		long targetCount,
		long paidCount,
		Long ledgerId,
		OffsetDateTime createdAt
) {

	public static DuesCreateResponse from(Dues dues) {
		return new DuesCreateResponse(dues.getId(), dues.getGroupId(), dues.getTitle(), dues.getAmount(),
				dues.getDueDate(), dues.getStatus(), dues.targetCount(), dues.paidCount(),
				dues.getLedgerId(), KoreanTime.toOffset(dues.getCreatedAt()));
	}
}
