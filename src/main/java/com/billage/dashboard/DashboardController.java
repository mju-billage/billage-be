package com.billage.dashboard;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.dashboard.dto.DashboardResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboardService;

	@GetMapping
	public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(@CurrentUserId Long userId,
			@PathVariable Long groupId,
			@RequestParam(defaultValue = "5") int recentEntrySize) {
		DashboardResponse response = dashboardService.getDashboard(groupId, userId, recentEntrySize);
		return ResponseEntity.ok(ApiResponse.of(response, "대시보드 조회에 성공했습니다."));
	}
}
