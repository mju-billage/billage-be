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
		long sizeBytes,
		OffsetDateTime createdAt
) {

	/**
	 * @param sizeBytes 담긴 증빙 파일의 용량 합계(바이트). 증빙이 없으면 0 이다.
	 */
	public static ArchiveSummaryResponse from(Archive archive, long sizeBytes) {
		return new ArchiveSummaryResponse(archive.getId(), archive.getTitle(),
				archive.getStartDate(), archive.getEndDate(),
				archive.getTotalIncome(), archive.getTotalExpense(), archive.balance(),
				archive.getLedgerCount(), archive.getEntryCount(), sizeBytes,
				KoreanTime.toOffset(archive.getCreatedAt()));
	}
}
