package com.billage.report.dto;

import com.billage.report.Report;

/** 보고서 전체 요약. 잔액은 저장하지 않고 수입 − 지출로 계산한 값이다. */
public record ReportSummary(
		Long totalIncome,
		Long totalExpense,
		Long balance,
		Long entryCount
) {

	public static ReportSummary from(Report report) {
		return new ReportSummary(report.getTotalIncome(), report.getTotalExpense(), report.getBalance(),
				report.getEntryCount());
	}
}
