package com.billage.group;

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
import com.billage.group.dto.GroupCreateRequest;
import com.billage.group.dto.GroupCreateResponse;
import com.billage.group.dto.GroupDetailResponse;
import com.billage.group.dto.GroupSummaryResponse;
import com.billage.group.dto.GroupUpdateRequest;
import com.billage.group.dto.GroupUpdateResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

	private final GroupService groupService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<GroupSummaryResponse>>> getMyGroups(@CurrentUserId Long userId) {
		List<GroupSummaryResponse> groups = groupService.getMyGroups(userId);
		String message = groups.isEmpty() ? "조회된 데이터가 없습니다." : "모임 목록 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(groups, message));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<GroupCreateResponse>> create(@CurrentUserId Long userId,
			@Valid @RequestBody GroupCreateRequest request) {
		GroupCreateResponse response = groupService.create(userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "모임 생성에 성공했습니다."));
	}

	@GetMapping("/{groupId}")
	public ResponseEntity<ApiResponse<GroupDetailResponse>> getDetail(@CurrentUserId Long userId,
			@PathVariable Long groupId) {
		return ResponseEntity.ok(
				ApiResponse.of(groupService.getDetail(groupId, userId), "모임 조회에 성공했습니다."));
	}

	@PatchMapping("/{groupId}")
	public ResponseEntity<ApiResponse<GroupUpdateResponse>> update(@CurrentUserId Long userId,
			@PathVariable Long groupId, @Valid @RequestBody GroupUpdateRequest request) {
		return ResponseEntity.ok(
				ApiResponse.of(groupService.update(groupId, userId, request), "모임 정보 수정에 성공했습니다."));
	}

	@DeleteMapping("/{groupId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUserId Long userId, @PathVariable Long groupId) {
		groupService.delete(groupId, userId);
	}
}
