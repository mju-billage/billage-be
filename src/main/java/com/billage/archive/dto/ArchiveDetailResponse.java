package com.billage.archive.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.billage.archive.Archive;
import com.billage.archive.ArchiveEntry;
import com.billage.archive.ArchiveLedger;
import com.billage.common.response.KoreanTime;
import com.billage.entry.ApprovalStatus;
import com.billage.entry.EntryType;

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

	public record Ledger(String folderName, String ledgerName, Long budget,
			long totalIncome, long totalExpense, long balance, List<Entry> entries) {
	}

	public record Entry(EntryType type, String title, long amount, LocalDate occurredOn, String memo,
			ApprovalStatus approvalStatus, String createdByName) {
	}

	public static ArchiveDetailResponse from(Archive archive) {
		List<Ledger> ledgers = archive.getLedgers().stream()
				.map(ArchiveDetailResponse::toLedger)
				.toList();

		return new ArchiveDetailResponse(archive.getId(), archive.getGroupId(), archive.getTitle(),
				archive.getStartDate(), archive.getEndDate(),
				new Summary(archive.getTotalIncome(), archive.getTotalExpense(), archive.balance(),
						archive.getEntryCount()),
				ledgers, KoreanTime.toOffset(archive.getCreatedAt()));
	}

	private static Ledger toLedger(ArchiveLedger ledger) {
		List<Entry> entries = ledger.getEntries().stream()
				.map(ArchiveDetailResponse::toEntry)
				.toList();
		return new Ledger(ledger.getFolderName(), ledger.getLedgerName(), ledger.getBudget(),
				ledger.getTotalIncome(), ledger.getTotalExpense(), ledger.balance(), entries);
	}

	private static Entry toEntry(ArchiveEntry entry) {
		return new Entry(entry.getType(), entry.getTitle(), entry.getAmount(), entry.getOccurredOn(),
				entry.getMemo(), entry.getApprovalStatus(), entry.getCreatedByName());
	}
}
