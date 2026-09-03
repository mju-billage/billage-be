package com.billage.auth.email.dto;

import java.time.OffsetDateTime;

/** 인증 확인 결과. */
public record EmailVerificationConfirmResponse(
		String email,
		boolean verified,
		OffsetDateTime verifiedAt
) {
}
