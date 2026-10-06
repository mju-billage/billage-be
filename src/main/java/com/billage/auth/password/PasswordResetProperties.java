package com.billage.auth.password;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 비밀번호 재설정 정책.
 *
 * @param maxSends          발송 창 안에서 한 계정에 보낼 임시 비밀번호 수.
 * @param sendWindowMinutes 발송 횟수를 세는 창(분).
 */
@ConfigurationProperties(prefix = "billage.auth.password-reset")
public record PasswordResetProperties(
		@DefaultValue("3") int maxSends,
		@DefaultValue("60") int sendWindowMinutes
) {
}
