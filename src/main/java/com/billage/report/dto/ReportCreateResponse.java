package com.billage.report.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.billage.common.response.KoreanTime;
import com.billage.report.Report;
import com.billage.report.ReportLedger;

/** 보고서 생성 응답. 상세와 달리 내역 목록 없이 장부별 요약만 담는다. */
public record ReportCreateResponse(
		Long reportId,
		String title,
		LocalDate startDate,
		LocalDate endDate,
		ReportSummary summary,
		List<LedgerSummary> ledgers,
		OffsetDateTime createdAt
) {

	public record LedgerSummary(
			Long ledgerId,
			String ledgerName,
			Long totalIncome,
			Long totalExpense,
			Long balance
	) {

		static LedgerSummary from(ReportLedger ledger) {
			return new LedgerSummary(ledger.getLedgerId(), ledger.getLedgerName(), ledger.getTotalIncome(),
					ledger.getTotalExpense(), ledger.getBalance());
		}
	}

	public static ReportCreateResponse from(Report report) {
		return new ReportCreateResponse(report.getId(), report.getTitle(), report.getStartDate(),
				report.getEndDate(), ReportSummary.from(report),
				report.getLedgers().stream().map(LedgerSummary::from).toList(),
				KoreanTime.toOffset(report.getCreatedAt()));
	}
}
