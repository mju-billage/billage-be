package com.billage.ocr;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.ocr.dto.ReceiptOcrResponse;

import lombok.RequiredArgsConstructor;

/**
 * 영수증 인식. 경로가 {@code /api/v1/files} 아래인 것은 인식 대상이 이미 업로드된 파일이기 때문이다.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class ReceiptOcrController {

	private final ReceiptOcrService receiptOcrService;

	@PostMapping("/{fileId}/ocr")
	public ResponseEntity<ApiResponse<ReceiptOcrResponse>> recognize(@CurrentUserId Long userId,
			@PathVariable Long fileId) {
		return ResponseEntity.ok(ApiResponse.of(receiptOcrService.recognize(fileId, userId),
				"영수증 인식에 성공했습니다."));
	}
}
