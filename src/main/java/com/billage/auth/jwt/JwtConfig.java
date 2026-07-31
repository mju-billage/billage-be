package com.billage.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * HS256 대칭키 기반 JWT 인코더·디코더 구성.
 * 서드파티 JWT 라이브러리 없이 Spring Security(Nimbus)로 발급·검증한다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	private final JwtProperties properties;

	public JwtConfig(JwtProperties properties) {
		this.properties = properties;
	}

	/** HS256은 최소 256비트(32바이트) 키를 요구한다. 짧은 키는 기동 시점에 명확히 실패시킨다. */
	private static final int MIN_SECRET_BYTES = 32;

	private SecretKey secretKey() {
		byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"jwt.secret 은 HS256 서명을 위해 최소 " + MIN_SECRET_BYTES + "바이트여야 합니다. (현재 "
							+ keyBytes.length + "바이트)");
		}
		return new SecretKeySpec(keyBytes, "HmacSHA256");
	}

	@Bean
	public JwtEncoder jwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey())
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(List.of(
				new JwtTimestampValidator(),
				new JwtClaimValidator<String>(JwtClaimNames.ISS, properties.issuer()::equals),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
						aud -> aud != null && aud.contains(properties.audience()))
		)));
		return decoder;
	}
}
