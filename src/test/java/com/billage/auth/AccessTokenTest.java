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
		String tampered = token.substring(0, token.length() - 2) + (token.endsWith("a") ? "b" : "a") + "c";

		assertThatThrownBy(() -> jwtDecoder.decode(tampered))
				.isInstanceOf(JwtException.class);
	}
}
