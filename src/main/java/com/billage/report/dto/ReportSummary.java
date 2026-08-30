package com.billage.report.dto;

import com.billage.report.Report;

/** 보고서 전체 요약. 잔액은 저장하지 않고 수입 − 지출로 계산한 값이다. */
public record ReportSummary(
		Long totalIncome,
		Long totalExpense,
		Long balance,
		Long entryCount,

		/**
		 * 기간별 보고서의 잔액 흐름(캐러셀 2페이지). 시작 잔액은 기간 시작일 직전까지의 누적 잔액이다.
		 * 장부별 보고서에는 해당 화면이 없어 둘 다 null 이다.
		 */
		Long openingBalance,
		Long closingBalance
) {

	public static ReportSummary from(Report report) {
		return new ReportSummary(report.getTotalIncome(), report.getTotalExpense(), report.getBalance(),
				report.getEntryCount(), report.getOpeningBalance(), report.getClosingBalance());
	}
}
