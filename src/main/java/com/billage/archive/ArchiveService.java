package com.billage.archive;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.archive.dto.ArchiveDetailResponse;
import com.billage.archive.dto.ArchiveSummaryResponse;
import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dues.Dues;
import com.billage.dues.DuesRepository;
import com.billage.dues.DuesStatus;
import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;
import com.billage.entry.EntryRepository;
import com.billage.entry.EntryType;
import com.billage.file.FileService;
import com.billage.folder.FolderRepository;
import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerRepository;
import com.billage.membership.GroupAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * 기록 보관(보관함). 모임의 현재 장부 전부를 스냅샷으로 뜬 뒤 <b>원본 폴더·장부·내역을 지워</b>
 * 모임을 비운다 — 화면이 "보관 후 장부는 수정이 불가합니다"라고 경고하고, 보관 직후 폴더 화면은
 * "새로운 폴더를 생성해주세요" 빈 화면이 된다.
 *
 * <p>되돌릴 수 없는 작업이라 총무만 실행한다.
 */
@Service
@RequiredArgsConstructor
public class ArchiveService {

	private final ArchiveRepository archiveRepository;
	private final LedgerRepository ledgerRepository;
	private final EntryRepository entryRepository;
	private final FolderRepository folderRepository;
	private final DuesRepository duesRepository;
	private final FileService fileService;
	private final GroupAccessGuard guard;

	/**
	 * 보관 실행.
	 *
	 * <p>진행 중인 회비가 남아 있으면 거부한다. 회비는 마감될 때 장부에 수입 내역을 만드는데,
	 * 그 장부가 먼저 사라지면 마감할 곳이 없어져 회비가 오도 가도 못하게 된다.
	 */
	@Transactional
	public ArchiveSummaryResponse create(Long groupId, Long userId, String title) {
		guard.requireOwner(groupId, userId);

		if (!duesRepository.findAllByGroupId(groupId).stream()
				.allMatch(dues -> dues.getStatus() == DuesStatus.CLOSED)) {
			throw new BusinessException(ErrorCode.ARCHIVE_BLOCKED_BY_OPEN_DUES);
		}

		List<Ledger> ledgers = ledgerRepository.findAllInGroup(groupId, null);
		if (ledgers.isEmpty()) {
			throw new BusinessException(ErrorCode.ARCHIVE_EMPTY);
		}

		List<Entry> entries = entryRepository.findAllByGroupId(groupId);
		if (entries.isEmpty()) {
			throw new BusinessException(ErrorCode.ARCHIVE_EMPTY);
		}

		Map<Long, ArchiveEntry> archived = new LinkedHashMap<>();
		Archive archive = snapshot(groupId, title.trim(), ledgers, entries, archived);
		archiveRepository.save(archive);

		// 증빙은 지우지 않고 주인만 보관 기록으로 옮긴다 — 보관인데 이미지가 사라지면 안 된다.
		fileService.moveReceiptsToArchive(archived);
		clearGroup(groupId);

		return ArchiveSummaryResponse.from(archive);
	}

	@Transactional(readOnly = true)
	public List<ArchiveSummaryResponse> getArchives(Long groupId, Long userId) {
		guard.requireMembership(groupId, userId);
		return archiveRepository.findAllByGroupId(groupId).stream()
				.map(ArchiveSummaryResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ArchiveDetailResponse getDetail(Long archiveId, Long userId) {
		Archive archive = archiveRepository.findWithLedgers(archiveId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ARCHIVE_NOT_FOUND));
		guard.requireMembership(archive.getGroupId(), userId);
		// 내역은 장부마다 지연 로딩된다 — 목록 화면이 아니라 한 건을 펼쳐 보는 화면이라 N+1 이 아니다.
		archive.getLedgers().forEach(ledger -> ledger.getEntries().size());

		List<Long> archiveEntryIds = archive.getLedgers().stream()
				.flatMap(ledger -> ledger.getEntries().stream())
				.map(ArchiveEntry::getId)
				.toList();
		return ArchiveDetailResponse.from(archive, fileService.getArchiveReceipts(archiveEntryIds));
	}

	/** 제목 변경. 담긴 내용은 바꿀 수 없다. */
	@Transactional
	public ArchiveSummaryResponse rename(Long archiveId, Long userId, String title) {
		Archive archive = findArchive(archiveId);
		guard.requireOwner(archive.getGroupId(), userId);

		String trimmed = title.trim();
		if (trimmed.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "보관 제목은 공백일 수 없습니다.");
		}
		archive.rename(trimmed);
		return ArchiveSummaryResponse.from(archive);
	}

	/** 삭제. 화면도 "삭제 이후에는 데이터 복구가 어렵습니다"라고 경고한다 — 되돌릴 수 없다. */
	@Transactional
	public void delete(Long archiveId, Long userId) {
		Archive archive = findArchive(archiveId);
		guard.requireOwner(archive.getGroupId(), userId);
		// 보관된 증빙은 이 기록에만 매달려 있다. 기록보다 먼저 지워야 외래키가 걸리지 않는다.
		fileService.deleteByArchive(archiveId);
		archiveRepository.delete(archive);
	}

	/** 모임 삭제 시 보관된 증빙도 함께 지운다. */
	private void deleteReceiptsOf(Archive archive) {
		fileService.deleteByArchive(archive.getId());
	}

	/** 모임 삭제용. 자식 스냅샷은 cascade + orphanRemoval 로 함께 지워진다. */
	@Transactional
	public void deleteByGroup(Long groupId) {
		List<Archive> archives = archiveRepository.findAllByGroupId(groupId);
		archives.forEach(this::deleteReceiptsOf);
		archiveRepository.deleteAll(archives);
	}

	private Archive snapshot(Long groupId, String title, List<Ledger> ledgers, List<Entry> entries,
			Map<Long, ArchiveEntry> archivedByEntryId) {
		Map<Long, List<Entry>> byLedger = new LinkedHashMap<>();
		for (Entry entry : entries) {
			byLedger.computeIfAbsent(entry.getLedger().getId(), key -> new java.util.ArrayList<>()).add(entry);
		}

		long totalIncome = sum(entries, EntryType.INCOME);
		long totalExpense = sum(entries, EntryType.EXPENSE);
		LocalDate startDate = entries.stream().map(Entry::getOccurredOn).min(Comparator.naturalOrder()).orElseThrow();
		LocalDate endDate = entries.stream().map(Entry::getOccurredOn).max(Comparator.naturalOrder()).orElseThrow();

		Archive archive = Archive.of(groupId, title, startDate, endDate, totalIncome, totalExpense,
				entries.size(), ledgers.size());

		for (Ledger ledger : ledgers) {
			List<Entry> ledgerEntries = byLedger.getOrDefault(ledger.getId(), List.of());
			ArchiveLedger archiveLedger = ArchiveLedger.of(
					ledger.getFolder() == null ? null : ledger.getFolder().getName(),
					ledger.getName(), ledger.getBudget(),
					sum(ledgerEntries, EntryType.INCOME), sum(ledgerEntries, EntryType.EXPENSE));
			for (Entry entry : ledgerEntries) {
				ArchiveEntry archiveEntry = ArchiveEntry.of(entry.getType(), entry.getTitle(), entry.getAmount(),
						entry.getOccurredOn(), entry.getMemo(), entry.getApprovalStatus(), entry.getCreatedByName());
				archiveLedger.addEntry(archiveEntry);
				archivedByEntryId.put(entry.getId(), archiveEntry);
			}
			archive.addLedger(archiveLedger);
		}
		return archive;
	}

	/**
	 * 원본을 비운다. 내역 → 장부 → 폴더 순으로 참조를 따라 지운다.
	 *
	 * <p>증빙은 지우지 않는다 — 이미 보관 기록으로 주인을 옮겨 두었다.
	 * 마감된 회비는 남기되, 그 회비가 들고 있던 수입 내역 ID 는 끊는다(그 내역이 방금 사라졌다).
	 */
	private void clearGroup(Long groupId) {
		duesRepository.findAllByGroupId(groupId).forEach(Dues::detachGeneratedEntry);
		entryRepository.deleteAllByGroupId(groupId);
		ledgerRepository.deleteAllByGroupId(groupId);
		folderRepository.deleteDeepestFirst(folderRepository.findAllByGroupId(groupId));
	}

	/**
	 * 합계는 <b>승인된 내역만</b> 센다(재무 데이터 처리 규칙). 승인 대기 내역도 보관 목록에는 남지만,
	 * 아직 장부에 반영되지 않은 금액이라 수입·지출·잔액에 섞이면 안 된다.
	 */
	private long sum(List<Entry> entries, EntryType type) {
		return entries.stream()
				.filter(entry -> entry.getApprovalStatus() == ApprovalStatus.APPROVED)
				.filter(entry -> entry.getType() == type)
				.mapToLong(Entry::getAmount)
				.sum();
	}

	private Archive findArchive(Long archiveId) {
		return archiveRepository.findById(archiveId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ARCHIVE_NOT_FOUND));
	}
}
