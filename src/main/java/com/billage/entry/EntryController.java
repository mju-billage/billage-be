package com.billage.entry;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.common.response.PageResponse;
import com.billage.entry.dto.EntryApproveResponse;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.entry.dto.EntryCreateResponse;
import com.billage.entry.dto.EntryDetailResponse;
import com.billage.entry.dto.EntrySummaryResponse;
import com.billage.entry.dto.EntryUpdateRequest;
import com.billage.entry.dto.EntryUpdateResponse;
import com.billage.entry.dto.GroupEntryListResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class EntryController {

	private final EntryService entryService;

	@GetMapping("/api/v1/ledgers/{ledgerId}/entries")
	public ResponseEntity<ApiResponse<PageResponse<EntrySummaryResponse>>> getEntries(@CurrentUserId Long userId,
			@PathVariable Long ledgerId,
			@RequestParam(required = false) EntryType type,
			@RequestParam(required = false) ApprovalStatus status,
			@RequestParam(required = false) String keyword,
			@PageableDefault(size = 20, sort = { "occurredOn", "id" },
					direction = Sort.Direction.DESC) Pageable pageable) {
		PageResponse<EntrySummaryResponse> entries =
				entryService.getEntries(ledgerId, userId, type, status, keyword, pageable);
		String message = entries.content().isEmpty() ? "조회된 데이터가 없습니다." : "내역 목록 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(entries, message));
	}

	/**
	 * 모임 전체 내역 목록. GNB 「내역」 탭·검색·필터 시트가 모두 이 API 를 쓴다.
	 *
	 * <p>장부 하나만 볼 때는 {@code /ledgers/{ledgerId}/entries} 를 쓴다 — 그쪽은 잔액 요약을 내지 않는다.
	 */
	@GetMapping("/api/v1/groups/{groupId}/entries")
	public ResponseEntity<ApiResponse<GroupEntryListResponse>> getGroupEntries(@CurrentUserId Long userId,
			@PathVariable Long groupId,
			@RequestParam(required = false) List<Long> ledgerIds,
			@RequestParam(required = false) EntryType type,
			@RequestParam(required = false) ApprovalStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String keyword,
			@PageableDefault(size = 20, sort = { "occurredOn", "id" },
					direction = Sort.Direction.DESC) Pageable pageable) {
		GroupEntryListResponse response = entryService.getGroupEntries(groupId, userId, ledgerIds, type, status,
				from, to, keyword, pageable);
		String message = response.entries().content().isEmpty()
				? "조회된 데이터가 없습니다."
				: "내역 목록 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(response, message));
	}

	@PostMapping("/api/v1/ledgers/{ledgerId}/entries")
	public ResponseEntity<ApiResponse<EntryCreateResponse>> create(@CurrentUserId Long userId,
			@PathVariable Long ledgerId, @Valid @RequestBody EntryCreateRequest request) {
		EntryCreateResponse response = entryService.create(ledgerId, userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "내역 등록에 성공했습니다."));
	}

	@GetMapping("/api/v1/entries/{entryId}")
	public ResponseEntity<ApiResponse<EntryDetailResponse>> getDetail(@CurrentUserId Long userId,
			@PathVariable Long entryId) {
		return ResponseEntity.ok(
				ApiResponse.of(entryService.getDetail(entryId, userId), "내역 조회에 성공했습니다."));
	}

	@PatchMapping("/api/v1/entries/{entryId}")
	public ResponseEntity<ApiResponse<EntryUpdateResponse>> update(@CurrentUserId Long userId,
			@PathVariable Long entryId, @Valid @RequestBody EntryUpdateRequest request) {
		return ResponseEntity.ok(
				ApiResponse.of(entryService.update(entryId, userId, request), "내역 수정에 성공했습니다."));
	}

	@DeleteMapping("/api/v1/entries/{entryId}")
	public ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable Long entryId) {
		entryService.delete(entryId, userId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/entries/{entryId}/approve")
	public ResponseEntity<ApiResponse<EntryApproveResponse>> approve(@CurrentUserId Long userId,
			@PathVariable Long entryId) {
		return ResponseEntity.ok(
				ApiResponse.of(entryService.approve(entryId, userId), "내역 승인에 성공했습니다."));
	}
}
