package com.billage.report.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.report.Report;

/** 보고서 목록 항목. */
public record ReportSummaryResponse(
		Long reportId,
		String title,
		LocalDate startDate,
		LocalDate endDate,
		Long ledgerCount,
		Long totalIncome,
		Long totalExpense,
		Long balance,
		OffsetDateTime createdAt
) {

	public static ReportSummaryResponse from(Report report) {
		return new ReportSummaryResponse(report.getId(), report.getTitle(), report.getStartDate(),
				report.getEndDate(), report.getLedgerCount(), report.getTotalIncome(), report.getTotalExpense(),
				report.getBalance(), KoreanTime.toOffset(report.getCreatedAt()));
	}
}
