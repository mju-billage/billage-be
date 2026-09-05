package com.billage.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 가입 약관 동의 정책.
 *
 * @param requiredForSignup 가입 요청에 {@code agreements} 를 요구할지. 이메일 인증과 같은 이유로 기본은 꺼 둔다 —
 *                          프론트가 약관 동의 화면을 붙이기 전에 켜면 기존 가입 흐름이 곧바로 막힌다.
 *                          동의 값이 오면 이 설정과 무관하게 항상 검증하고 기록한다.
 */
@ConfigurationProperties(prefix = "billage.auth.terms")
public record TermsProperties(
		@DefaultValue("false") boolean requiredForSignup
) {
}
