package com.billage.common.mail;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Gmail SMTP 발송. 발신 도메인이 없어 Gmail 계정과 앱 비밀번호로 보낸다.
 *
 * <p>{@code spring.mail.*} 자동 설정을 쓰지 않고 클라이언트를 직접 만든다 — 자동 설정을 켜면
 * 헬스 체크가 호출될 때마다 Gmail 에 로그인해서, Gmail 이 잠깐 느리기만 해도 배포가 롤백된다.
 *
 * <p>Gmail 무료 계정은 하루 500통까지다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "billage.mail.sender", havingValue = "SMTP")
public class SmtpMailSender implements MailSender {

	private final JavaMailSenderImpl client;
	private final String from;

	public SmtpMailSender(MailProperties properties) {
		MailProperties.Smtp smtp = properties.smtp();
		// 기동 시 막는다 — 환경변수를 빠뜨렸다고 발송이 조용히 멈추면 안 된다.
		if (isBlank(smtp.username())) {
			throw new IllegalStateException("billage.mail.smtp.username 설정이 필요합니다. (GMAIL_USERNAME)");
		}
		if (isBlank(smtp.password())) {
			throw new IllegalStateException("billage.mail.smtp.password 설정이 필요합니다. (GMAIL_APP_PASSWORD)");
		}
		this.from = isBlank(properties.from()) ? smtp.username() : properties.from();

		String timeout = String.valueOf(smtp.timeout().toMillis());
		Properties javaMail = new Properties();
		javaMail.put("mail.smtp.auth", "true");
		javaMail.put("mail.smtp.starttls.enable", String.valueOf(smtp.starttls()));
		javaMail.put("mail.smtp.starttls.required", String.valueOf(smtp.starttls()));
		javaMail.put("mail.smtp.connectiontimeout", timeout);
		javaMail.put("mail.smtp.timeout", timeout);
		javaMail.put("mail.smtp.writetimeout", timeout);

		this.client = new JavaMailSenderImpl();
		client.setHost(smtp.host());
		client.setPort(smtp.port());
		client.setUsername(smtp.username());
		// 구글이 앱 비밀번호를 네 글자씩 띄워 보여 준다. 그대로 붙여 넣어도 되게 공백을 뺀다.
		client.setPassword(smtp.password().replace(" ", ""));
		client.setDefaultEncoding(StandardCharsets.UTF_8.name());
		client.setJavaMailProperties(javaMail);
	}

	@Override
	public void send(String to, String subject, String body) {
		try {
			MimeMessage message = client.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
			helper.setFrom(from);
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(body);
			client.send(message);
		} catch (MailException | MessagingException e) {
			// 수신자에게 원인을 그대로 보여 줄 값이 아니다 — 로그로만 남기고 공통 오류로 바꾼다.
			// 한도 초과처럼 실패가 몰릴 때 가입자 주소가 로그에 쌓이지 않게 도메인만 남긴다.
			log.error("SMTP 발송 실패. toDomain={} reason={}", to.substring(to.indexOf('@') + 1), e.getMessage());
			throw new BusinessException(ErrorCode.MAIL_SEND_FAILED);
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
