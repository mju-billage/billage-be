package com.billage.ledger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.billage.ledger.dto.GroupLedgerResponse;
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

		List<Ledger> ledgers = ledgerRepository.findAllByFolderId(folderId);
		Map<Long, LedgerStats> stats = statsOf(ledgers);

		return ledgers.stream()
				.map(ledger -> LedgerSummaryResponse.of(ledger, stats.get(ledger.getId())))
				.toList();
	}

	/**
	 * 모임의 모든 장부. 폴더 구조와 무관하게 평평한 목록이 필요한 화면들이 쓴다 —
	 * 예산 설정, 내역 추가·필터·회비 생성의 장부 선택 바텀시트.
	 *
	 * <p>폴더 트리를 받아 폴더마다 {@link #getLedgers} 를 부르면 폴더 수만큼 호출이 늘고,
	 * 무엇보다 <b>최상위 영역으로 올라온 장부가 빠진다</b>.
	 */
	@Transactional(readOnly = true)
	public List<GroupLedgerResponse> getGroupLedgers(Long groupId, Long userId, String keyword) {
		guard.requireMembership(groupId, userId);

		String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		List<Ledger> ledgers = ledgerRepository.findAllInGroup(groupId, normalizedKeyword);
		Map<Long, LedgerStats> stats = statsOf(ledgers);

		return ledgers.stream()
				.map(ledger -> GroupLedgerResponse.of(ledger, stats.get(ledger.getId())))
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
			// `@Size(min = 1)` 은 " " 를 통과시켜 trim 후 빈 이름이 되므로 여기서 막는다.
			String name = request.name().trim();
			if (name.isEmpty()) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST, "장부 이름은 공백일 수 없습니다.");
			}
			ledger.rename(name);
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

	/**
	 * 여러 장부의 집계를 한 번에. 장부마다 {@link #statsOf(Ledger)} 를 부르면 장부 수의 두 배만큼
	 * 쿼리가 나간다(합계 + 건수). 내역이 하나도 없는 장부도 0 으로 채워 돌려준다.
	 */
	private Map<Long, LedgerStats> statsOf(List<Ledger> ledgers) {
		List<Long> ledgerIds = ledgers.stream().map(Ledger::getId).toList();
		Map<Long, Map<EntryType, Long>> sums = entryRepository.sumApprovedByLedger(ledgerIds);
		Map<Long, Long> counts = entryRepository.countByLedgers(ledgerIds);

		return ledgerIds.stream().collect(Collectors.toMap(id -> id, id -> {
			Map<EntryType, Long> sum = sums.getOrDefault(id, Map.of());
			return new LedgerStats(sum.getOrDefault(EntryType.INCOME, 0L),
					sum.getOrDefault(EntryType.EXPENSE, 0L), counts.getOrDefault(id, 0L));
		}));
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
