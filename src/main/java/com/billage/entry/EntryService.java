package com.billage.entry;

import java.util.Map;

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
import com.billage.entry.dto.EntryUpdateRequest;
import com.billage.entry.dto.EntryUpdateResponse;
import com.billage.file.FileService;
import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerRepository;
import com.billage.membership.GroupAccessGuard;
import com.billage.membership.GroupMembership;
import com.billage.user.User;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 수입·지출 내역.
 *
 * <p>수정·삭제는 <b>총무(OWNER) 전용</b>이다 — 기획 「일반 권한 모임 관리자」 문서에서
 * "상세 내역 수정 및 삭제 금지"로 확정(2026-08-17). 일반 관리자는 본인이 등록한 승인 대기 내역도 고칠 수 없다.
 */
@Service
@RequiredArgsConstructor
public class EntryService {

	private final EntryRepository entryRepository;
	private final LedgerRepository ledgerRepository;
	private final UserRepository userRepository;
	private final FileService fileService;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public PageResponse<EntrySummaryResponse> getEntries(Long ledgerId, Long userId, EntryType type,
			ApprovalStatus status, String keyword, Pageable pageable) {
		Ledger ledger = findLedger(ledgerId);
		guard.requireMembership(ledger.getGroup().getId(), userId);

		String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		Page<Entry> entries = entryRepository.search(ledgerId, type, status, normalizedKeyword, pageable);
		Map<Long, Long> receiptCounts = fileService.countReceipts(
				entries.getContent().stream().map(Entry::getId).toList());

		return PageResponse.of(entries,
				entry -> EntrySummaryResponse.of(entry, receiptCounts.getOrDefault(entry.getId(), 0L)));
	}

	/**
	 * 내역 등록. 총무가 등록하면 즉시 승인, 일반 권한 관리자가 등록하면 승인 대기로 생성된다.
	 */
	@Transactional
	public EntryCreateResponse create(Long ledgerId, Long userId, EntryCreateRequest request) {
		Ledger ledger = findLedger(ledgerId);
		GroupMembership author = guard.requireMembership(ledger.getGroup().getId(), userId);
		String authorName = userName(userId);

		Entry entry = entryRepository.save(Entry.create(ledger, author, authorName, request.type(),
				requireNonBlank(request.title()), request.amount(), request.occurredOn(), request.memo()));
		fileService.linkReceipts(entry, request.receiptFileIds(), userId);

		return EntryCreateResponse.of(entry, fileService.getReceipts(entry.getId()));
	}

	@Transactional(readOnly = true)
	public EntryDetailResponse getDetail(Long entryId, Long userId) {
		Entry entry = findEntry(entryId);
		guard.requireMembership(entry.getGroupId(), userId);

		return EntryDetailResponse.of(entry, fileService.getReceipts(entryId));
	}

	/**
	 * 내역 수정(총무 전용). 승인 완료 내역도 그대로 수정하며 재승인을 요구하지 않는다.
	 * 유형(type)은 바꿀 수 없고, 작성자·승인자 기록은 등록·승인 시점 값을 유지한다.
	 */
	@Transactional
	public EntryUpdateResponse update(Long entryId, Long userId, EntryUpdateRequest request) {
		Entry entry = findEntry(entryId);
		guard.requireOwner(entry.getGroupId(), userId);

		String title = request.title() == null ? null : request.title().trim();
		if (title != null && title.isEmpty()) {
			// `@Size(min = 1)` 은 " " 를 통과시켜 trim 후 빈 내역명이 되므로 여기서 막는다.
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "내역명은 공백일 수 없습니다.");
		}
		entry.update(title, request.amount(), request.occurredOn(), request.memo());
		fileService.replaceReceipts(entry, request.receiptFileIds(), userId);

		return EntryUpdateResponse.from(entry);
	}

	/**
	 * 내역 삭제(총무 전용). 승인 상태와 무관하게 연결된 증빙까지 완전 삭제하며 이력을 남기지 않는다.
	 */
	@Transactional
	public void delete(Long entryId, Long userId) {
		Entry entry = findEntry(entryId);
		guard.requireOwner(entry.getGroupId(), userId);

		fileService.deleteByEntry(entryId);
		entryRepository.delete(entry);
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
		// 컨트롤러의 @NotBlank 를 거치지 않는 직접 호출도 500 이 아니라 INVALID_REQUEST 로 응답하게 한다.
		if (title == null || title.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "내역명은 공백일 수 없습니다.");
		}
		return title.trim();
	}

	private Entry findEntry(Long entryId) {
		return entryRepository.findById(entryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ENTRY_NOT_FOUND));
	}
}
