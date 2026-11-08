package com.billage.helpcenter;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.common.response.PageResponse;
import com.billage.helpcenter.dto.FaqResponse;
import com.billage.helpcenter.dto.InquiryCreateRequest;
import com.billage.helpcenter.dto.NoticeDetailResponse;
import com.billage.helpcenter.dto.NoticeSummaryResponse;
import com.billage.helpcenter.dto.TermsResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HelpCenterController {

	private final HelpCenterService helpCenterService;

	@GetMapping("/api/v1/faqs")
	public ResponseEntity<ApiResponse<List<FaqResponse>>> getFaqs() {
		List<FaqResponse> faqs = helpCenterService.getFaqs();
		String message = faqs.isEmpty() ? "조회된 데이터가 없습니다." : "자주 묻는 질문 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(faqs, message));
	}

	@GetMapping("/api/v1/notices")
	public ResponseEntity<ApiResponse<PageResponse<NoticeSummaryResponse>>> getNotices(
			@PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
			Pageable pageable) {
		PageResponse<NoticeSummaryResponse> notices = helpCenterService.getNotices(pageable);
		String message = notices.content().isEmpty() ? "조회된 데이터가 없습니다." : "공지사항 목록 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(notices, message));
	}

	@GetMapping("/api/v1/notices/{noticeId}")
	public ResponseEntity<ApiResponse<NoticeDetailResponse>> getNotice(@PathVariable Long noticeId) {
		return ResponseEntity.ok(ApiResponse.of(helpCenterService.getNotice(noticeId), "공지사항 조회에 성공했습니다."));
	}

	@GetMapping("/api/v1/terms/{termType}")
	public ResponseEntity<ApiResponse<TermsResponse>> getTerms(@PathVariable TermType termType) {
		return ResponseEntity.ok(ApiResponse.of(helpCenterService.getTerms(termType), "약관 조회에 성공했습니다."));
	}

	@PostMapping("/api/v1/inquiries")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void createInquiry(@CurrentUserId Long userId, @Valid @RequestBody InquiryCreateRequest request) {
		helpCenterService.createInquiry(userId, request);
	}
}
