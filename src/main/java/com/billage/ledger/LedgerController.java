package com.billage.ledger;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.ledger.dto.BudgetUpdateRequest;
import com.billage.ledger.dto.BudgetUpdateResponse;
import com.billage.ledger.dto.GroupLedgerResponse;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.ledger.dto.LedgerCreateResponse;
import com.billage.ledger.dto.LedgerDetailResponse;
import com.billage.ledger.dto.LedgerSummaryResponse;
import com.billage.ledger.dto.LedgerUpdateRequest;
import com.billage.ledger.dto.LedgerUpdateResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class LedgerController {

	private final LedgerService ledgerService;

	@GetMapping("/api/v1/folders/{folderId}/ledgers")
	public ResponseEntity<ApiResponse<List<LedgerSummaryResponse>>> getLedgers(@CurrentUserId Long userId,
			@PathVariable Long folderId) {
		List<LedgerSummaryResponse> ledgers = ledgerService.getLedgers(folderId, userId);
		String message = ledgers.isEmpty() ? "조회된 데이터가 없습니다." : "장부 목록 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(ledgers, message));
	}

	/**
	 * 모임의 모든 장부. 폴더 구조를 가로질러 평평하게 내려 준다 —
	 * 예산 설정 화면과 장부 선택 바텀시트(내역 추가 / 내역 필터 / 회비 생성)가 쓴다.
	 */
	@GetMapping("/api/v1/groups/{groupId}/ledgers")
	public ResponseEntity<ApiResponse<List<GroupLedgerResponse>>> getGroupLedgers(@CurrentUserId Long userId,
			@PathVariable Long groupId, @RequestParam(required = false) String keyword) {
		List<GroupLedgerResponse> ledgers = ledgerService.getGroupLedgers(groupId, userId, keyword);
		String message = ledgers.isEmpty() ? "조회된 데이터가 없습니다." : "장부 목록 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(ledgers, message));
	}

	@PostMapping("/api/v1/folders/{folderId}/ledgers")
	public ResponseEntity<ApiResponse<LedgerCreateResponse>> create(@CurrentUserId Long userId,
			@PathVariable Long folderId, @Valid @RequestBody LedgerCreateRequest request) {
		LedgerCreateResponse response = ledgerService.create(folderId, userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "장부 생성에 성공했습니다."));
	}

	@GetMapping("/api/v1/ledgers/{ledgerId}")
	public ResponseEntity<ApiResponse<LedgerDetailResponse>> getDetail(@CurrentUserId Long userId,
			@PathVariable Long ledgerId) {
		return ResponseEntity.ok(
				ApiResponse.of(ledgerService.getDetail(ledgerId, userId), "장부 조회에 성공했습니다."));
	}

	@PatchMapping("/api/v1/ledgers/{ledgerId}")
	public ResponseEntity<ApiResponse<LedgerUpdateResponse>> update(@CurrentUserId Long userId,
			@PathVariable Long ledgerId, @Valid @RequestBody LedgerUpdateRequest request) {
		return ResponseEntity.ok(
				ApiResponse.of(ledgerService.update(ledgerId, userId, request), "장부 수정에 성공했습니다."));
	}

	@PatchMapping("/api/v1/ledgers/{ledgerId}/budget")
	public ResponseEntity<ApiResponse<BudgetUpdateResponse>> changeBudget(@CurrentUserId Long userId,
			@PathVariable Long ledgerId, @RequestBody BudgetUpdateRequest request) {
		return ResponseEntity.ok(
				ApiResponse.of(ledgerService.changeBudget(ledgerId, userId, request), "장부 예산 수정에 성공했습니다."));
	}

	@DeleteMapping("/api/v1/ledgers/{ledgerId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUserId Long userId, @PathVariable Long ledgerId) {
		ledgerService.delete(ledgerId, userId);
	}
}
