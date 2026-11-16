package com.billage.common.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 메일 설정. 발송 구현은 {@code billage.mail.sender} 로 고른다
 * ({@link LogMailSender} / {@link SmtpMailSender}).
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {
}
