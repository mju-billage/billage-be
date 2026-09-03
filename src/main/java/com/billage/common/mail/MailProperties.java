package com.billage.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 메일 발송 정책.
 *
 * @param sender 발송 수단. 로컬·테스트는 {@code LOG}, dev·prod 는 {@code SES}.
 * @param from   발신 주소. SES 에서 검증된 주소여야 한다(도메인 검증 전에는 개별 주소 검증).
 */
@ConfigurationProperties(prefix = "billage.mail")
public record MailProperties(
		@DefaultValue("LOG") SenderType sender,
		@DefaultValue("no-reply@billage.app") String from
) {
	public enum SenderType {
		LOG,
		SES
	}
}
