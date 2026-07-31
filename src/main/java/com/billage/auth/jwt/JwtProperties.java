package com.billage.auth.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 관련 설정값. 비밀키·만료시간은 환경변수로 주입한다.
 *
 * @param issuer                 발급자 (iss)
 * @param audience               대상 (aud)
 * @param secret                 HS256 서명 비밀키 (최소 32바이트)
 * @param accessTokenValidity    Access Token 유효기간 (기본 30분)
 * @param refreshTokenValidity   Refresh Token 유효기간 (설정값으로 분리)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
		String issuer,
		String audience,
		String secret,
		Duration accessTokenValidity,
		Duration refreshTokenValidity
) {
}
