package com.billage.dues;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.billage.dues.dto.DuesCloseResponse;
import com.billage.dues.dto.DuesCreateRequest;
import com.billage.dues.dto.DuesCreateResponse;
import com.billage.dues.dto.DuesDetailResponse;
import com.billage.dues.dto.DuesSummaryResponse;
import com.billage.dues.dto.DuesTargetResponse;
import com.billage.dues.dto.DuesUpdateRequest;
import com.billage.dues.dto.DuesUpdateResponse;
import com.billage.dues.dto.PaymentStatusUpdateRequest;
import com.billage.dues.dto.PaymentStatusUpdateResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class DuesController {

	private final DuesService duesService;

	@GetMapping("/api/v1/groups/{groupId}/dues")
	public ResponseEntity<ApiResponse<PageResponse<DuesSummaryResponse>>> getDuesList(
			@CurrentUserId Long userId, @PathVariable Long groupId,
			@RequestParam(required = false) DuesStatus status,
			@PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
		PageResponse<DuesSummaryResponse> duesList = duesService.getDuesList(groupId, userId, status, pageable);
		String message = duesList.content().isEmpty() ? "조회된 데이터가 없습니다." : "회비 목록 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(duesList, message));
	}

	@PostMapping("/api/v1/groups/{groupId}/dues")
	public ResponseEntity<ApiResponse<DuesCreateResponse>> create(@CurrentUserId Long userId,
			@PathVariable Long groupId, @Valid @RequestBody DuesCreateRequest request) {
		DuesCreateResponse response = duesService.create(groupId, userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "회비 생성에 성공했습니다."));
	}

	@GetMapping("/api/v1/dues/{duesId}")
	public ResponseEntity<ApiResponse<DuesDetailResponse>> getDetail(@CurrentUserId Long userId,
			@PathVariable Long duesId) {
		return ResponseEntity.ok(
				ApiResponse.of(duesService.getDetail(duesId, userId), "회비 조회에 성공했습니다."));
	}

	@PatchMapping("/api/v1/dues/{duesId}")
	public ResponseEntity<ApiResponse<DuesUpdateResponse>> update(@CurrentUserId Long userId,
			@PathVariable Long duesId, @Valid @RequestBody DuesUpdateRequest request) {
		return ResponseEntity.ok(
				ApiResponse.of(duesService.update(duesId, userId, request), "회비 수정에 성공했습니다."));
	}

	@DeleteMapping("/api/v1/dues/{duesId}")
	public ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable Long duesId) {
		duesService.delete(duesId, userId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/v1/dues/{duesId}/members")
	public ResponseEntity<ApiResponse<List<DuesTargetResponse>>> getTargets(@CurrentUserId Long userId,
			@PathVariable Long duesId,
			@RequestParam(required = false) PaymentStatus status,
			@RequestParam(required = false) String keyword) {
		List<DuesTargetResponse> targets = duesService.getTargets(duesId, userId, status, keyword);
		String message = targets.isEmpty() ? "조회된 데이터가 없습니다." : "납부 대상 목록 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(targets, message));
	}

	@PatchMapping("/api/v1/dues/{duesId}/members/{memberId}")
	public ResponseEntity<ApiResponse<PaymentStatusUpdateResponse>> changePaymentStatus(
			@CurrentUserId Long userId, @PathVariable Long duesId, @PathVariable Long memberId,
			@Valid @RequestBody PaymentStatusUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.of(
				duesService.changePaymentStatus(duesId, memberId, userId, request),
				"납부 상태 변경에 성공했습니다."));
	}

	@PostMapping("/api/v1/dues/{duesId}/close")
	public ResponseEntity<ApiResponse<DuesCloseResponse>> close(@CurrentUserId Long userId,
			@PathVariable Long duesId) {
		return ResponseEntity.ok(
				ApiResponse.of(duesService.close(duesId, userId), "회비 마감에 성공했습니다."));
	}
}
