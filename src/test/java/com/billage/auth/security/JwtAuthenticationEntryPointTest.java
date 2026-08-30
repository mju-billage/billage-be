package com.billage.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import com.billage.auth.jwt.JwtConfig;
import com.billage.auth.jwt.JwtProperties;

/**
 * 401 사유 구분 계약.
 *
 * <p>만료({@code TOKEN_EXPIRED})와 손상({@code TOKEN_INVALID})의 판정이 Nimbus 의 <b>설명 문구</b>에 기대고 있어
 * 라이브러리 업그레이드로 조용히 깨질 수 있다. 실제 디코더가 만드는 예외를 그대로 태워 회귀를 막는다.
 */
class JwtAuthenticationEntryPointTest {

	private static final String SECRET = "test-only-billage-jwt-secret-please-change-32bytes+";
	private static final String ISSUER = "billage-api";
	private static final String AUDIENCE = "billage-app";

	private JwtEncoder encoder;
	private JwtDecoder decoder;
	private JwtAuthenticationEntryPoint entryPoint;

	@BeforeEach
	void setUp() {
		JwtProperties properties = new JwtProperties(ISSUER, AUDIENCE, SECRET,
				Duration.ofMinutes(30), Duration.ofDays(14));
		JwtConfig config = new JwtConfig(properties);
		encoder = config.jwtEncoder();
		decoder = config.jwtDecoder();
		entryPoint = new JwtAuthenticationEntryPoint(new SecurityErrorResponder());
	}

	@Test
	@DisplayName("만료된 Access Token 은 TOKEN_EXPIRED 로 구분된다")
	void expiredToken() throws Exception {
		Instant past = Instant.now().minus(Duration.ofHours(2));
		String expired = encode(past, past.plus(Duration.ofMinutes(30)));

		MockHttpServletResponse response = commence(bearerExceptionFor(expired));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("\"code\":\"TOKEN_EXPIRED\"");
	}

	@Test
	@DisplayName("서명이 깨진 토큰은 TOKEN_INVALID 로 구분된다")
	void tamperedToken() throws Exception {
		Instant now = Instant.now();
		String valid = encode(now, now.plus(Duration.ofMinutes(30)));
		String tampered = valid.substring(0, valid.lastIndexOf('.') + 1) + "Zm9yZ2Vk";

		MockHttpServletResponse response = commence(bearerExceptionFor(tampered));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("\"code\":\"TOKEN_INVALID\"");
	}

	@Test
	@DisplayName("토큰이 아예 없으면 UNAUTHORIZED")
	void noToken() throws Exception {
		MockHttpServletResponse response = commence(new BadCredentialsException("no token"));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
	}

	@Test
	@DisplayName("유효한 토큰은 디코딩된다 — 위 두 케이스가 설정 오류로 통과한 게 아님을 보장")
	void validTokenDecodes() {
		Instant now = Instant.now();
		String valid = encode(now, now.plus(Duration.ofMinutes(30)));

		assertThat(decoder.decode(valid).getSubject()).isEqualTo("42");
	}

	/** 리소스 서버 필터가 하는 것과 같은 방식으로 디코딩 실패를 인증 예외로 감싼다. */
	private InvalidBearerTokenException bearerExceptionFor(String token) {
		try {
			decoder.decode(token);
		} catch (JwtException e) {
			return new InvalidBearerTokenException(e.getMessage(), e);
		}
		throw new IllegalStateException("디코딩이 실패해야 하는 토큰인데 통과했다");
	}

	private MockHttpServletResponse commence(org.springframework.security.core.AuthenticationException exception)
			throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		entryPoint.commence(null, response, exception);
		return response;
	}

	private String encode(Instant issuedAt, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.audience(List.of(AUDIENCE))
				.subject("42")
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}
}
