package com.billage.member;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.member.dto.MemberBulkCreateRequest;
import com.billage.member.dto.MemberBulkDeleteRequest;
import com.billage.member.dto.MemberDetailResponse;
import com.billage.member.dto.MemberPaymentListResponse;
import com.billage.member.dto.MemberCreateRequest;
import com.billage.member.dto.MemberResponse;
import com.billage.member.dto.MemberUpdateRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembers(@CurrentUserId Long userId,
			@PathVariable Long groupId, @RequestParam(required = false) String keyword) {
		List<MemberResponse> members = memberService.getMembers(groupId, userId, keyword);
		String message = members.isEmpty() ? "조회된 데이터가 없습니다." : "모임원 목록 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(members, message));
	}

	@GetMapping("/{memberId}")
	public ResponseEntity<ApiResponse<MemberDetailResponse>> getMember(@CurrentUserId Long userId,
			@PathVariable Long groupId, @PathVariable Long memberId) {
		MemberDetailResponse member = memberService.getMember(groupId, userId, memberId);
		return ResponseEntity.ok(ApiResponse.of(member, "모임원 조회에 성공했습니다."));
	}

	/** 모임원이 낸 회비 목록. 기간은 납부 시각 기준이다. */
	@GetMapping("/{memberId}/payments")
	public ResponseEntity<ApiResponse<MemberPaymentListResponse>> getPayments(@CurrentUserId Long userId,
			@PathVariable Long groupId, @PathVariable Long memberId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		MemberPaymentListResponse payments = memberService.getPayments(groupId, userId, memberId, from, to);
		String message = payments.payments().isEmpty() ? "조회된 데이터가 없습니다." : "납부 내역 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(payments, message));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<MemberResponse>> addMember(@CurrentUserId Long userId,
			@PathVariable Long groupId, @Valid @RequestBody MemberCreateRequest request) {
		MemberResponse response = memberService.addMember(groupId, userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "모임원 등록에 성공했습니다."));
	}

	/** 일괄 추가. 쉼표·띄어쓰기·줄바꿈으로 구분한 이름만 받는다. */
	@PostMapping("/bulk")
	public ResponseEntity<ApiResponse<List<MemberResponse>>> addMembers(@CurrentUserId Long userId,
			@PathVariable Long groupId, @Valid @RequestBody MemberBulkCreateRequest request) {
		List<MemberResponse> members = memberService.addMembers(groupId, userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(members, "모임원 %d명 등록에 성공했습니다.".formatted(members.size())));
	}

	@PatchMapping("/{memberId}")
	public ResponseEntity<ApiResponse<MemberResponse>> updateMember(@CurrentUserId Long userId,
			@PathVariable Long groupId, @PathVariable Long memberId,
			@Valid @RequestBody MemberUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.of(
				memberService.updateMember(groupId, userId, memberId, request), "모임원 수정에 성공했습니다."));
	}

	/** 모임원 일괄 삭제. 하나라도 다른 모임의 모임원이면 전부 취소된다. */
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMembers(@CurrentUserId Long userId, @PathVariable Long groupId,
			@Valid @RequestBody MemberBulkDeleteRequest request) {
		memberService.removeMembers(groupId, userId, request);
	}

	@DeleteMapping("/{memberId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMember(@CurrentUserId Long userId, @PathVariable Long groupId, @PathVariable Long memberId) {
		memberService.removeMember(groupId, userId, memberId);
	}
}
