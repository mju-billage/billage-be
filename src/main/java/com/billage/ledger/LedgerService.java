package com.billage.ledger;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.entry.EntryRepository;
import com.billage.entry.EntryType;
import com.billage.file.FileService;
import com.billage.folder.Folder;
import com.billage.folder.FolderRepository;
import com.billage.ledger.dto.BudgetUpdateRequest;
import com.billage.ledger.dto.BudgetUpdateResponse;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.ledger.dto.LedgerCreateResponse;
import com.billage.ledger.dto.LedgerDetailResponse;
import com.billage.ledger.dto.LedgerSummaryResponse;
import com.billage.ledger.dto.LedgerUpdateRequest;
import com.billage.ledger.dto.LedgerUpdateResponse;
import com.billage.membership.GroupAccessGuard;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LedgerService {

	private final LedgerRepository ledgerRepository;
	private final FolderRepository folderRepository;
	private final EntryRepository entryRepository;
	private final FileService fileService;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public List<LedgerSummaryResponse> getLedgers(Long folderId, Long userId) {
		Folder folder = findFolder(folderId);
		guard.requireMembership(folder.getGroup().getId(), userId);

		return ledgerRepository.findAllByFolderId(folderId).stream()
				.map(ledger -> LedgerSummaryResponse.of(ledger, statsOf(ledger)))
				.toList();
	}

	@Transactional
	public LedgerCreateResponse create(Long folderId, Long userId, LedgerCreateRequest request) {
		Folder folder = findFolder(folderId);
		guard.requireOwner(folder.getGroup().getId(), userId);
		validateBudget(request.budget());

		return LedgerCreateResponse.from(
				ledgerRepository.save(Ledger.create(folder, request.name().trim(), request.budget())));
	}

	@Transactional(readOnly = true)
	public LedgerDetailResponse getDetail(Long ledgerId, Long userId) {
		Ledger ledger = findLedger(ledgerId);
		guard.requireMembership(ledger.getGroup().getId(), userId);

		return LedgerDetailResponse.of(ledger, statsOf(ledger));
	}

	/**
	 * 장부 수정(이름·폴더 이동). 예산은 별도 API 로 변경한다.
	 */
	@Transactional
	public LedgerUpdateResponse update(Long ledgerId, Long userId, LedgerUpdateRequest request) {
		Ledger ledger = findLedger(ledgerId);
		guard.requireOwner(ledger.getGroup().getId(), userId);

		if (request.name() != null) {
			ledger.rename(request.name().trim());
		}
		if (request.folderId() != null) {
			Folder folder = findFolder(request.folderId());
			if (!folder.getGroup().getId().equals(ledger.getGroup().getId())) {
				throw new BusinessException(ErrorCode.GROUP_MISMATCH);
			}
			ledger.moveTo(folder);
		}

		return LedgerUpdateResponse.from(ledger);
	}

	@Transactional
	public BudgetUpdateResponse changeBudget(Long ledgerId, Long userId, BudgetUpdateRequest request) {
		Ledger ledger = findLedger(ledgerId);
		guard.requireOwner(ledger.getGroup().getId(), userId);
		validateBudget(request.budget());

		ledger.changeBudget(request.budget());

		return BudgetUpdateResponse.of(ledger, statsOf(ledger));
	}

	/**
	 * 장부 삭제. 장부와 종속 내역·증빙 파일을 완전히 삭제한다.
	 */
	@Transactional
	public void delete(Long ledgerId, Long userId) {
		Ledger ledger = findLedger(ledgerId);
		guard.requireOwner(ledger.getGroup().getId(), userId);

		fileService.deleteByLedger(ledgerId);
		entryRepository.deleteAllByLedgerId(ledgerId);
		ledgerRepository.delete(ledger);
	}

	/**
	 * 장부 집계. 승인된 내역만 합산하며 잔액은 저장하지 않고 매번 계산한다.
	 * {@code entryCount} 는 승인 여부와 관계없는 전체 내역 수다.
	 */
	private LedgerStats statsOf(Ledger ledger) {
		Map<EntryType, Long> approvedSums = entryRepository.sumApprovedByType(ledger.getId());

		return new LedgerStats(
				approvedSums.getOrDefault(EntryType.INCOME, 0L),
				approvedSums.getOrDefault(EntryType.EXPENSE, 0L),
				entryRepository.countByLedgerId(ledger.getId()));
	}

	private void validateBudget(Long budget) {
		if (budget != null && (budget < 0 || budget > Ledger.MAX_BUDGET)) {
			throw new BusinessException(ErrorCode.INVALID_BUDGET);
		}
	}

	private Folder findFolder(Long folderId) {
		return folderRepository.findById(folderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));
	}

	private Ledger findLedger(Long ledgerId) {
		return ledgerRepository.findById(ledgerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LEDGER_NOT_FOUND));
	}
}
