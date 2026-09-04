package com.billage.archive.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.billage.archive.Archive;
import com.billage.common.response.KoreanTime;

/** 보관함 카드. 화면은 제목과 "YYYY.MM.DD - YYYY.MM.DD" 기간을 보여 준다. */
public record ArchiveSummaryResponse(
		Long archiveId,
		String title,
		LocalDate startDate,
		LocalDate endDate,
		long totalIncome,
		long totalExpense,
		long balance,
		long ledgerCount,
		long entryCount,
		OffsetDateTime createdAt
) {

	public static ArchiveSummaryResponse from(Archive archive) {
		return new ArchiveSummaryResponse(archive.getId(), archive.getTitle(),
				archive.getStartDate(), archive.getEndDate(),
				archive.getTotalIncome(), archive.getTotalExpense(), archive.balance(),
				archive.getLedgerCount(), archive.getEntryCount(),
				KoreanTime.toOffset(archive.getCreatedAt()));
	}
}
