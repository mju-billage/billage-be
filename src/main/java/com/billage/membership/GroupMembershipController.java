package com.billage.membership;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.membership.dto.InvitationResponse;
import com.billage.membership.dto.JoinGroupRequest;
import com.billage.membership.dto.JoinGroupResponse;
import com.billage.membership.dto.MembershipResponse;
import com.billage.membership.dto.RoleUpdateRequest;
import com.billage.membership.dto.RoleUpdateResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupMembershipController {

	private final GroupMembershipService groupMembershipService;

	@GetMapping("/{groupId}/memberships")
	public ResponseEntity<ApiResponse<List<MembershipResponse>>> getMemberships(@CurrentUserId Long userId,
			@PathVariable Long groupId) {
		List<MembershipResponse> memberships = groupMembershipService.getMemberships(groupId, userId);
		return ResponseEntity.ok(ApiResponse.of(memberships, "모임 관리자 목록 조회에 성공했습니다."));
	}

	@PostMapping("/{groupId}/invitations")
	public ResponseEntity<ApiResponse<InvitationResponse>> createInvitation(@CurrentUserId Long userId,
			@PathVariable Long groupId) {
		InvitationResponse response = groupMembershipService.createInvitation(groupId, userId);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "초대 코드 생성에 성공했습니다."));
	}

	@PostMapping("/join")
	public ResponseEntity<ApiResponse<JoinGroupResponse>> join(@CurrentUserId Long userId,
			@Valid @RequestBody JoinGroupRequest request) {
		JoinGroupResponse response = groupMembershipService.join(userId, request.invitationCode());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "모임 참여에 성공했습니다."));
	}

	@PatchMapping("/{groupId}/memberships/{membershipId}")
	public ResponseEntity<ApiResponse<RoleUpdateResponse>> changeRole(@CurrentUserId Long userId,
			@PathVariable Long groupId, @PathVariable Long membershipId,
			@Valid @RequestBody RoleUpdateRequest request) {
		RoleUpdateResponse response = groupMembershipService.changeRole(groupId, userId, membershipId, request);
		return ResponseEntity.ok(ApiResponse.of(response, "관리자 권한 수정에 성공했습니다."));
	}

	/** 모임 관리자 내보내기(총무 전용). 그 사람이 남긴 과거 내역은 지우지 않는다. */
	@DeleteMapping("/{groupId}/memberships/{membershipId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMembership(@CurrentUserId Long userId, @PathVariable Long groupId,
			@PathVariable Long membershipId) {
		groupMembershipService.removeMembership(groupId, userId, membershipId);
	}

	@PostMapping("/{groupId}/leave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@CurrentUserId Long userId, @PathVariable Long groupId) {
		groupMembershipService.leave(groupId, userId);
	}
}
