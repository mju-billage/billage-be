package com.billage.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.billage.auth.jwt.JwtTokenProvider;
import com.billage.support.IntegrationTest;

class AccessTokenTest extends IntegrationTest {

	@Autowired
	JwtTokenProvider jwtTokenProvider;
	@Autowired
	JwtDecoder jwtDecoder;

	@Test
	void 발급된_Access_Token은_요구된_클레임을_갖고_검증에_통과한다() {
		String token = jwtTokenProvider.createAccessToken(42L);

		Jwt jwt = jwtDecoder.decode(token);

		assertThat(jwt.getClaimAsString("iss")).isEqualTo("billage-api");
		assertThat(jwt.getAudience()).contains("billage-app");
		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(jwt.getId()).isNotBlank(); // jti
		assertThat(jwt.getIssuedAt()).isNotNull();
		assertThat(jwt.getExpiresAt()).isNotNull();
		assertThat(jwt.getClaimAsString("tokenType")).isEqualTo("access");
		// 개인정보·모임별 권한은 포함하지 않는다
		assertThat(jwt.getClaims()).doesNotContainKeys("email", "roles", "authorities");
	}

	@Test
	void 위조된_토큰은_검증에_실패한다() {
		String token = jwtTokenProvider.createAccessToken(1L);
		// header.payload.signature 중 payload 세그먼트를 한 글자 바꾼다 → 서명 불일치로 항상 검출
		String[] parts = token.split("\\.");
		char[] payload = parts[1].toCharArray();
		payload[0] = payload[0] == 'A' ? 'B' : 'A';
		String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

		assertThatThrownBy(() -> jwtDecoder.decode(tampered))
				.isInstanceOf(JwtException.class);
	}
}
