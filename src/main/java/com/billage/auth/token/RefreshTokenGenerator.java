package com.billage.auth.token;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * 예측 불가능한 Refresh Token 원문을 생성한다.
 * 256비트 보안 난수를 URL-safe Base64로 인코딩한다.
 */
@Component
public class RefreshTokenGenerator {

	private static final int TOKEN_BYTE_LENGTH = 32; // 256-bit

	private final SecureRandom secureRandom = new SecureRandom();
	private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
		secureRandom.nextBytes(bytes);
		return encoder.encodeToString(bytes);
	}
}
