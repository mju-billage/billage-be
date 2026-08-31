package com.billage.statistics;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.statistics.dto.StatisticsResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class StatisticsController {

	private final StatisticsService statisticsService;

	/**
	 * 통계/분석. 「폴더 메인 &gt; 통계/분석」과 「더보기 &gt; 소비 통계/분석」이 같은 데이터를 쓴다.
	 */
	@GetMapping("/api/v1/groups/{groupId}/statistics")
	public ResponseEntity<ApiResponse<StatisticsResponse>> getStatistics(@CurrentUserId Long userId,
			@PathVariable Long groupId) {
		StatisticsResponse statistics = statisticsService.getStatistics(groupId, userId);
		String message = statistics.mostActiveLedger() == null
				? "분석할 데이터가 없습니다."
				: "통계 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(statistics, message));
	}
}
