package com.billage.auth.email;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.email.dto.EmailVerificationConfirmRequest;
import com.billage.auth.email.dto.EmailVerificationConfirmResponse;
import com.billage.auth.email.dto.EmailVerificationSendRequest;
import com.billage.auth.email.dto.EmailVerificationSendResponse;
import com.billage.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 회원가입 이메일 인증. 화면 「이메일 인증」(COM-4-PAGE-01-0)이 쓰며 로그인 전에 호출하므로 인증 없이 연다.
 */
@RestController
@RequestMapping("/api/v1/auth/email-verifications")
@RequiredArgsConstructor
public class EmailVerificationController {

	private final EmailVerificationService emailVerificationService;

	/** 코드 발송. 화면 진입 시 자동 발송과 「재전송하기」가 같은 엔드포인트를 쓴다. */
	@PostMapping
	public ResponseEntity<ApiResponse<EmailVerificationSendResponse>> send(
			@Valid @RequestBody EmailVerificationSendRequest request) {
		EmailVerificationSendResponse response = emailVerificationService.send(request.email());
		return ResponseEntity.ok(ApiResponse.of(response, "인증 코드를 보냈습니다."));
	}

	/** 코드 확인. 「다음으로」가 호출한다. */
	@PostMapping("/confirm")
	public ResponseEntity<ApiResponse<EmailVerificationConfirmResponse>> confirm(
			@Valid @RequestBody EmailVerificationConfirmRequest request) {
		EmailVerificationConfirmResponse response =
				emailVerificationService.confirm(request.email(), request.code());
		return ResponseEntity.ok(ApiResponse.of(response, "이메일 인증에 성공했습니다."));
	}
}
