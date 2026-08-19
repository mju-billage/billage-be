package com.billage.dues.dto;

import java.time.LocalDate;

import com.billage.dues.Dues;
import com.billage.dues.DuesStatus;

/** 회비 목록 항목. 인원 집계는 목록 조회에서 한 번에 세어 넘겨받는다. */
public record DuesSummaryResponse(
		Long duesId,
		String title,
		Long amount,
		LocalDate dueDate,
		DuesStatus status,
		long paidCount,
		long unpaidCount,
		long targetCount,
		Long ledgerId,
		String ledgerName
) {

	public static DuesSummaryResponse of(Dues dues, long paidCount, long targetCount, String ledgerName) {
		return new DuesSummaryResponse(dues.getId(), dues.getTitle(), dues.getAmount(), dues.getDueDate(),
				dues.getStatus(), paidCount, targetCount - paidCount, targetCount,
				dues.getLedgerId(), ledgerName);
	}
}
