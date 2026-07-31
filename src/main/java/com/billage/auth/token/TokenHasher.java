package com.billage.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Refresh Token 원문을 SHA-256으로 해싱한다. 저장·조회 모두 해시로만 이뤄진다.
 * 토큰이 고엔트로피 랜덤 문자열이므로 솔트 없이 SHA-256으로 충분하다(무차별 대입 불가).
 */
@Component
public class TokenHasher {

	public String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256은 모든 JVM에 존재하므로 도달 불가
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
		}
	}
}
