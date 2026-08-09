package com.billage.group;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.billage.group.dto.CreateGroupRequest;
import com.billage.group.dto.GroupResponse;
import com.billage.group.dto.GroupSummaryResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

	private final GroupService groupService;

	@PostMapping
	public ResponseEntity<GroupResponse> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateGroupRequest request) {
		Long userId = Long.valueOf(jwt.getSubject());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(groupService.createGroup(userId, request.name()));
	}

	@GetMapping
	public ResponseEntity<List<GroupSummaryResponse>> myGroups(@AuthenticationPrincipal Jwt jwt) {
		Long userId = Long.valueOf(jwt.getSubject());
		return ResponseEntity.ok(groupService.getMyGroups(userId));
	}

	@GetMapping("/{groupId}")
	public ResponseEntity<GroupResponse> detail(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long groupId) {
		Long userId = Long.valueOf(jwt.getSubject());
		return ResponseEntity.ok(groupService.getGroup(userId, groupId));
	}
}
