package com.billage.entry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.response.PageResponse;
import com.billage.entry.dto.EntryApproveResponse;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.entry.dto.EntryCreateResponse;
import com.billage.entry.dto.EntryDetailResponse;
import com.billage.entry.dto.EntrySummaryResponse;
import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerRepository;
import com.billage.membership.GroupAccessGuard;
import com.billage.membership.GroupMembership;
import com.billage.user.User;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 수입·지출 내역. 수정·삭제는 권한 정책 확정 전이라 구현하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class EntryService {

	private final EntryRepository entryRepository;
	private final LedgerRepository ledgerRepository;
	private final UserRepository userRepository;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public PageResponse<EntrySummaryResponse> getEntries(Long ledgerId, Long userId, EntryType type,
			ApprovalStatus status, String keyword, Pageable pageable) {
		Ledger ledger = findLedger(ledgerId);
		guard.requireMembership(ledger.getGroup().getId(), userId);

		String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		Page<Entry> entries = entryRepository.search(ledgerId, type, status, normalizedKeyword, pageable);

		return PageResponse.of(entries, EntrySummaryResponse::from);
	}

	/**
	 * 내역 등록. 총무가 등록하면 즉시 승인, 일반 권한 관리자가 등록하면 승인 대기로 생성된다.
	 */
	@Transactional
	public EntryCreateResponse create(Long ledgerId, Long userId, EntryCreateRequest request) {
		Ledger ledger = findLedger(ledgerId);
		GroupMembership author = guard.requireMembership(ledger.getGroup().getId(), userId);
		String authorName = userName(userId);

		Entry entry = Entry.create(ledger, author, authorName, request.type(),
				requireNonBlank(request.title()), request.amount(), request.occurredOn(), request.memo());

		return EntryCreateResponse.from(entryRepository.save(entry));
	}

	@Transactional(readOnly = true)
	public EntryDetailResponse getDetail(Long entryId, Long userId) {
		Entry entry = findEntry(entryId);
		guard.requireMembership(entry.getGroupId(), userId);

		return EntryDetailResponse.from(entry);
	}

	/**
	 * 총무 승인. 승인 즉시 잔액·통계에 반영된다.
	 */
	@Transactional
	public EntryApproveResponse approve(Long entryId, Long userId) {
		Entry entry = findEntry(entryId);
		guard.requireOwner(entry.getGroupId(), userId);

		entry.approve(userId, userName(userId));

		return EntryApproveResponse.from(entry);
	}

	private String userName(Long userId) {
		return userRepository.findById(userId).map(User::getName)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	private Ledger findLedger(Long ledgerId) {
		return ledgerRepository.findById(ledgerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LEDGER_NOT_FOUND));
	}

	/**
	 * 공백 전용 내역명 차단. `@NotBlank` 는 컨트롤러 경로에만 적용되므로 Service 에서도 막는다.
	 */
	private String requireNonBlank(String title) {
		String trimmed = title.trim();
		if (trimmed.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "내역명은 공백일 수 없습니다.");
		}
		return trimmed;
	}

	private Entry findEntry(Long entryId) {
		return entryRepository.findById(entryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ENTRY_NOT_FOUND));
	}
}
