package com.billage.member;

import java.util.List;

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

	@PostMapping
	public ResponseEntity<ApiResponse<MemberResponse>> addMember(@CurrentUserId Long userId,
			@PathVariable Long groupId, @Valid @RequestBody MemberCreateRequest request) {
		MemberResponse response = memberService.addMember(groupId, userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "모임원 등록에 성공했습니다."));
	}

	@PatchMapping("/{memberId}")
	public ResponseEntity<ApiResponse<MemberResponse>> updateMember(@CurrentUserId Long userId,
			@PathVariable Long groupId, @PathVariable Long memberId,
			@Valid @RequestBody MemberUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.of(
				memberService.updateMember(groupId, userId, memberId, request), "모임원 수정에 성공했습니다."));
	}

	@DeleteMapping("/{memberId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMember(@CurrentUserId Long userId, @PathVariable Long groupId, @PathVariable Long memberId) {
		memberService.removeMember(groupId, userId, memberId);
	}
}
