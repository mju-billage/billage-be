package com.billage.ledger;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
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
	 * 장부 삭제. 장부와 종속 내역을 완전히 삭제한다.
	 * 내역(Entry) 도메인 구현 시 이 메서드에 내역 삭제를 추가해야 한다.
	 */
	@Transactional
	public void delete(Long ledgerId, Long userId) {
		Ledger ledger = findLedger(ledgerId);
		guard.requireOwner(ledger.getGroup().getId(), userId);

		ledgerRepository.delete(ledger);
	}

	/**
	 * 장부 집계. 내역 도메인 구현 전까지는 0을 반환한다 — 실제 집계 쿼리를 붙일 단일 지점.
	 */
	private LedgerStats statsOf(Ledger ledger) {
		return LedgerStats.EMPTY;
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
