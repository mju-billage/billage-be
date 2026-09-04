package com.billage.auth.email.dto;

import java.time.OffsetDateTime;

/**
 * 발송 결과. 화면이 남은 시간을 스스로 세기 위해 만료 시각과 유효 시간을 함께 준다 —
 * 기기 시계가 어긋나 있어도 {@code expiresIn} 으로 카운트다운할 수 있다.
 */
public record EmailVerificationSendResponse(
		String email,
		OffsetDateTime expiresAt,
		int expiresIn
) {
}
