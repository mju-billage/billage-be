package com.billage.common.mail;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 메일 발송 정책.
 *
 * @param sender 발송 수단. 로컬·테스트는 {@code LOG}, dev·prod 는 {@code SMTP}.
 * @param from   발신 주소. 비우면 SMTP 계정 주소를 쓴다 — Gmail 은 어차피 로그인한 계정으로 발신자를 바꿔 보낸다.
 */
@ConfigurationProperties(prefix = "billage.mail")
public record MailProperties(
		@DefaultValue("LOG") SenderType sender,
		String from,
		@DefaultValue Smtp smtp
) {
	public enum SenderType {
		LOG,
		SMTP
	}

	/**
	 * Gmail SMTP. 발신 도메인이 없어 Gmail 계정으로 보낸다.
	 *
	 * @param username Gmail 주소.
	 * @param password 앱 비밀번호(계정 비밀번호가 아니다). 커밋 금지 — 환경변수로만 주입한다.
	 * @param starttls STARTTLS 강제 여부. 테스트에서 가짜 서버를 붙일 때만 끈다.
	 * @param timeout  연결·응답 제한. 메일 발송이 요청 스레드를 오래 잡지 않게 한다.
	 */
	public record Smtp(
			@DefaultValue("smtp.gmail.com") String host,
			@DefaultValue("587") int port,
			String username,
			String password,
			@DefaultValue("true") boolean starttls,
			@DefaultValue("5s") Duration timeout
	) {
	}
}
