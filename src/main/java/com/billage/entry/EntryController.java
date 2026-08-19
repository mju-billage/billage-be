package com.billage.entry;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

	@PostMapping("/api/v1/entries/{entryId}/approve")
	public ResponseEntity<ApiResponse<EntryApproveResponse>> approve(@CurrentUserId Long userId,
			@PathVariable Long entryId) {
		return ResponseEntity.ok(
				ApiResponse.of(entryService.approve(entryId, userId), "내역 승인에 성공했습니다."));
	}
}
