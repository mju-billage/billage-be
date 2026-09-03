package com.billage.common.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 메일을 실제로 보내지 않고 로그로 남긴다. 로컬 개발과 테스트에서 인증 코드를 확인하는 용도다.
 * 운영에서 이 구현이 선택되면 사용자는 메일을 받지 못하므로 {@code billage.mail.sender} 설정을 확인해야 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "billage.mail.sender", havingValue = "LOG", matchIfMissing = true)
public class LogMailSender implements MailSender {

	@Override
	public void send(String to, String subject, String body) {
		log.info("[메일 발송 생략 - LOG 모드] to={} subject={}\n{}", to, subject, body);
	}
}
