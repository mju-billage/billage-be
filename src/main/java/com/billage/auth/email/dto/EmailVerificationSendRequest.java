package com.billage.auth.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 인증 코드 발송·재전송 요청. 재전송도 같은 요청을 다시 보낸다. */
public record EmailVerificationSendRequest(
		@NotBlank(message = "이메일은 필수입니다.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		String email
) {
}
