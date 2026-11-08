package com.billage.report.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.billage.common.response.KoreanTime;
import com.billage.entry.EntryType;
import com.billage.file.dto.ReceiptFileResponse;
import com.billage.report.Report;
import com.billage.report.ReportEntry;
import com.billage.report.ReportLedger;
import com.billage.report.ReportType;

/**
 * 보고서 상세. 웹뷰 렌더링에 필요한 JSON 만 담으며 PDF·Excel 다운로드는 MVP 범위 밖이다.
 * 모든 값은 생성 시점 스냅샷이라 원본 장부·내역이 바뀌어도 달라지지 않는다.
 * <b>예외는 내역의 증빙({@code receiptFiles})</b> — 스냅샷이 아니라 원본 내역에 지금 연결된 파일이며,
 * 원본 내역이 삭제·보관되면 빈 배열이 된다.
 */
public record ReportDetailResponse(
		Long reportId,
		Long groupId,
		String title,
		ReportType reportType,
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

		static LedgerDetail from(ReportLedger ledger, Map<Long, List<ReceiptFileResponse>> receiptsByEntryId) {
			return new LedgerDetail(ledger.getLedgerName(), ledger.getTotalIncome(), ledger.getTotalExpense(),
					ledger.getBalance(),
					ledger.getEntries().stream().map(entry -> EntryLine.from(entry, receiptsByEntryId)).toList());
		}
	}

	public record EntryLine(
			EntryType type,
			String title,
			Long amount,
			LocalDate occurredOn,
			/** 보고서 생성 시점의 메모(스냅샷). */
			String memo,
			/** 원본 내역의 <b>현재</b> 증빙. 스냅샷이 아니다. 원본이 삭제·보관됐으면 빈 배열. */
			List<ReceiptFileResponse> receiptFiles
	) {

		static EntryLine from(ReportEntry entry, Map<Long, List<ReceiptFileResponse>> receiptsByEntryId) {
			List<ReceiptFileResponse> receipts = entry.getEntryId() == null
					? List.of()
					: receiptsByEntryId.getOrDefault(entry.getEntryId(), List.of());
			return new EntryLine(entry.getType(), entry.getTitle(), entry.getAmount(), entry.getOccurredOn(),
					entry.getMemo(), receipts);
		}
	}

	public static ReportDetailResponse of(Report report, List<ReportLedger> ledgers,
			Map<Long, List<ReceiptFileResponse>> receiptsByEntryId) {
		return new ReportDetailResponse(report.getId(), report.getGroupId(), report.getTitle(),
				report.getReportType(),
				report.getStartDate(), report.getEndDate(), ReportSummary.from(report),
				ledgers.stream().map(ledger -> LedgerDetail.from(ledger, receiptsByEntryId)).toList(),
				KoreanTime.toOffset(report.getCreatedAt()));
	}
}
