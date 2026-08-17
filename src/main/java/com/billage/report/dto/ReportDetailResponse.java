package com.billage.report.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.billage.common.response.KoreanTime;
import com.billage.entry.EntryType;
import com.billage.report.Report;
import com.billage.report.ReportEntry;
import com.billage.report.ReportLedger;

/**
 * 보고서 상세. 웹뷰 렌더링에 필요한 JSON 만 담으며 PDF·Excel 다운로드는 MVP 범위 밖이다.
 * 모든 값은 생성 시점 스냅샷이라 원본 장부·내역이 바뀌어도 달라지지 않는다.
 */
public record ReportDetailResponse(
		Long reportId,
		Long groupId,
		String title,
		LocalDate startDate,
		LocalDate endDate,
		ReportSummary summary,
		List<LedgerDetail> ledgers,
		OffsetDateTime createdAt
) {

	public record LedgerDetail(
			String ledgerName,
			Long totalIncome,
			Long totalExpense,
			Long balance,
			List<EntryLine> entries
	) {

		static LedgerDetail from(ReportLedger ledger) {
			return new LedgerDetail(ledger.getLedgerName(), ledger.getTotalIncome(), ledger.getTotalExpense(),
					ledger.getBalance(), ledger.getEntries().stream().map(EntryLine::from).toList());
		}
	}

	public record EntryLine(
			EntryType type,
			String title,
			Long amount,
			LocalDate occurredOn
	) {

		static EntryLine from(ReportEntry entry) {
			return new EntryLine(entry.getType(), entry.getTitle(), entry.getAmount(), entry.getOccurredOn());
		}
	}

	public static ReportDetailResponse of(Report report, List<ReportLedger> ledgers) {
		return new ReportDetailResponse(report.getId(), report.getGroupId(), report.getTitle(),
				report.getStartDate(), report.getEndDate(), ReportSummary.from(report),
				ledgers.stream().map(LedgerDetail::from).toList(),
				KoreanTime.toOffset(report.getCreatedAt()));
	}
}
