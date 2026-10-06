package com.billage.archive.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.billage.archive.Archive;
import com.billage.archive.ArchiveEntry;
import com.billage.archive.ArchiveLedger;
import com.billage.common.response.KoreanTime;
import com.billage.entry.ApprovalStatus;
import com.billage.entry.EntryType;
import com.billage.file.dto.ReceiptFileResponse;

/** 「기록보기」가 여는 상세. 보고서 상세와 같은 모양이라 화면이 렌더링 코드를 나눠 쓸 수 있다. */
public record ArchiveDetailResponse(
		Long archiveId,
		Long groupId,
		String title,
		LocalDate startDate,
		LocalDate endDate,
		Summary summary,
		List<Ledger> ledgers,
		OffsetDateTime createdAt
) {

	public record Summary(long totalIncome, long totalExpense, long balance, long entryCount) {
	}

	/**
	 * @param startDate 이 장부에 담긴 내역 중 가장 이른 발생일. 내역이 없으면 null.
	 * @param endDate   가장 늦은 발생일. 장부에는 기간이라는 값이 따로 없어 내역에서 구한다.
	 */
	public record Ledger(String folderName, String ledgerName, Long budget,
			LocalDate startDate, LocalDate endDate,
			long totalIncome, long totalExpense, long balance, List<Entry> entries) {
	}

	public record Entry(EntryType type, String title, long amount, LocalDate occurredOn, String memo,
			ApprovalStatus approvalStatus, String createdByName, List<ReceiptFileResponse> receiptFiles) {
	}

	/**
	 * @param receiptsByArchiveEntryId 보관된 내역 ID 로 묶은 증빙. 보관은 증빙 이미지도 함께 남긴다.
	 */
	public static ArchiveDetailResponse from(Archive archive,
			Map<Long, List<ReceiptFileResponse>> receiptsByArchiveEntryId) {
		List<Ledger> ledgers = archive.getLedgers().stream()
				.map(ledger -> toLedger(ledger, receiptsByArchiveEntryId))
				.toList();

		return new ArchiveDetailResponse(archive.getId(), archive.getGroupId(), archive.getTitle(),
				archive.getStartDate(), archive.getEndDate(),
				new Summary(archive.getTotalIncome(), archive.getTotalExpense(), archive.balance(),
						archive.getEntryCount()),
				ledgers, KoreanTime.toOffset(archive.getCreatedAt()));
	}

	private static Ledger toLedger(ArchiveLedger ledger,
			Map<Long, List<ReceiptFileResponse>> receiptsByArchiveEntryId) {
		List<Entry> entries = ledger.getEntries().stream()
				.map(entry -> toEntry(entry, receiptsByArchiveEntryId))
				.toList();
		LocalDate startDate = ledger.getEntries().stream().map(ArchiveEntry::getOccurredOn)
				.min(Comparator.naturalOrder()).orElse(null);
		LocalDate endDate = ledger.getEntries().stream().map(ArchiveEntry::getOccurredOn)
				.max(Comparator.naturalOrder()).orElse(null);
		return new Ledger(ledger.getFolderName(), ledger.getLedgerName(), ledger.getBudget(),
				startDate, endDate,
				ledger.getTotalIncome(), ledger.getTotalExpense(), ledger.balance(), entries);
	}

	private static Entry toEntry(ArchiveEntry entry,
			Map<Long, List<ReceiptFileResponse>> receiptsByArchiveEntryId) {
		return new Entry(entry.getType(), entry.getTitle(), entry.getAmount(), entry.getOccurredOn(),
				entry.getMemo(), entry.getApprovalStatus(), entry.getCreatedByName(),
				receiptsByArchiveEntryId.getOrDefault(entry.getId(), List.of()));
	}
}
