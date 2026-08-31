package com.billage.dashboard;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.dashboard.dto.DashboardResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboardService;

	@GetMapping("/api/v1/groups/{groupId}/dashboard")
	public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(@CurrentUserId Long userId,
			@PathVariable Long groupId,
			@RequestParam(defaultValue = "5") int recentEntrySize) {
		DashboardResponse response = dashboardService.getDashboard(groupId, userId, recentEntrySize);
		return ResponseEntity.ok(ApiResponse.of(response, "대시보드 조회에 성공했습니다."));
	}

	/**
	 * 「캘린더 전체보기」(월간). 대시보드 응답의 {@code calendar} 와 같은 형식이며 기간만 한 달이다.
	 *
	 * <p>날짜를 눌렀을 때 뜨는 하단 리스트는 별도 API 가 없다 — 모임 전체 내역 목록을
	 * {@code from}·{@code to} 에 같은 날짜를 넣어 부르면 된다.
	 */
	@GetMapping("/api/v1/groups/{groupId}/calendar")
	public ResponseEntity<ApiResponse<DashboardResponse.Calendar>> getCalendar(@CurrentUserId Long userId,
			@PathVariable Long groupId,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth) {
		DashboardResponse.Calendar calendar = dashboardService.getCalendar(groupId, userId, yearMonth);
		String message = calendar.days().isEmpty() ? "조회된 데이터가 없습니다." : "캘린더 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(calendar, message));
	}
}
