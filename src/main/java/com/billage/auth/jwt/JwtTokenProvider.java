package com.billage.auth.jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Access Token 발급 컴포넌트.
 * 클레임: iss, aud, sub(userId), iat, exp, jti, tokenType=access.
 * 개인정보·모임별 권한은 담지 않는다(권한은 요청 시 DB에서 확인).
 */
@Component
public class JwtTokenProvider {

	private static final String TOKEN_TYPE_CLAIM = "tokenType";
	private static final String ACCESS_TOKEN_TYPE = "access";

	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;

	public JwtTokenProvider(JwtEncoder jwtEncoder, JwtProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	public String createAccessToken(Long userId) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(properties.accessTokenValidity());

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.audience(List.of(properties.audience()))
				.subject(String.valueOf(userId))
				.issuedAt(now)
				.expiresAt(expiresAt)
				.id(UUID.randomUUID().toString())
				.claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
				.build();

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/** 초 단위 Access Token 만료시간(응답의 accessTokenExpiresIn). */
	public long accessTokenExpiresInSeconds() {
		return properties.accessTokenValidity().toSeconds();
	}
}
