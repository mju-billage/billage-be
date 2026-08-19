package com.billage.dues.dto;

import java.time.LocalDate;

import com.billage.dues.Dues;
import com.billage.dues.DuesStatus;

/** 회비 수정 응답. */
public record DuesUpdateResponse(
		Long duesId,
		String title,
		Long amount,
		LocalDate dueDate,
		DuesStatus status,
		long targetCount,
		long paidCount,
		Long ledgerId
) {

	public static DuesUpdateResponse from(Dues dues) {
		return new DuesUpdateResponse(dues.getId(), dues.getTitle(), dues.getAmount(), dues.getDueDate(),
				dues.getStatus(), dues.targetCount(), dues.paidCount(), dues.getLedgerId());
	}
}
