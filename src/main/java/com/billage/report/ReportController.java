package com.billage.report;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.common.response.PageResponse;
import com.billage.report.dto.ReportCreateRequest;
import com.billage.report.dto.ReportCreateResponse;
import com.billage.report.dto.ReportDetailResponse;
import com.billage.report.dto.ReportSummaryResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	@GetMapping("/api/v1/groups/{groupId}/reports")
	public ResponseEntity<ApiResponse<PageResponse<ReportSummaryResponse>>> getReports(@CurrentUserId Long userId,
			@PathVariable Long groupId,
			@RequestParam(required = false) ReportType reportType,
			@RequestParam(required = false) String keyword,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		PageResponse<ReportSummaryResponse> reports =
				reportService.getReports(groupId, userId, reportType, keyword, pageable);
		String message = reports.content().isEmpty() ? "조회된 데이터가 없습니다." : "보고서 목록 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(reports, message));
	}

	@PostMapping("/api/v1/groups/{groupId}/reports")
	public ResponseEntity<ApiResponse<ReportCreateResponse>> create(@CurrentUserId Long userId,
			@PathVariable Long groupId, @Valid @RequestBody ReportCreateRequest request) {
		ReportCreateResponse response = reportService.create(groupId, userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "보고서 생성에 성공했습니다."));
	}

	@GetMapping("/api/v1/reports/{reportId}")
	public ResponseEntity<ApiResponse<ReportDetailResponse>> getDetail(@CurrentUserId Long userId,
			@PathVariable Long reportId) {
		return ResponseEntity.ok(
				ApiResponse.of(reportService.getDetail(reportId, userId), "보고서 조회에 성공했습니다."));
	}
}
