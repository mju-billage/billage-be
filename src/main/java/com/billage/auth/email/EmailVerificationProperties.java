package com.billage.auth.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 이메일 인증 정책. 기본값은 화면명세(COM-4-PAGE-01-0)의 "6자리 · 03:00" 을 그대로 따른다.
 *
 * @param ttlSeconds        코드 유효 시간(초). 화면 타이머와 같은 값이어야 한다.
 * @param maxAttempts       코드 대조 시도 상한. 6자리는 100만 가지뿐이라 상한이 없으면 무차별 대입이 가능하다.
 * @param maxSends          발송 창 안에서 허용할 발송 횟수(최초 발송 포함).
 * @param sendWindowMinutes 발송 횟수를 세는 창(분).
 * @param requiredForSignup 가입 시 인증 완료를 요구할지. 프론트가 인증 화면을 붙이기 전까지는 꺼 둔다.
 */
@ConfigurationProperties(prefix = "billage.auth.email-verification")
public record EmailVerificationProperties(
		@DefaultValue("180") int ttlSeconds,
		@DefaultValue("5") int maxAttempts,
		@DefaultValue("5") int maxSends,
		@DefaultValue("60") int sendWindowMinutes,
		@DefaultValue("false") boolean requiredForSignup
) {
}
