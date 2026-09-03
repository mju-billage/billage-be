package com.billage.common.mail;

import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 메일을 실제로 보내지 않고 로그로 남긴다. 로컬 개발과 테스트에서 인증 코드를 확인하는 용도다.
 *
 * <p>이 구현이 실서버에서 선택되면 <b>가입자가 인증 코드를 영영 받지 못하는데 오류도 나지 않는다</b> —
 * 조용히 망가지는 종류라, 배포 환경에서는 아예 뜨지 못하게 막는다. dev 는 SES 비용 없이 확인하려고
 * 일부러 이 모드를 쓸 수 있어 경고만 남기고, prod 는 시작을 실패시킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "billage.mail.sender", havingValue = "LOG", matchIfMissing = true)
public class LogMailSender implements MailSender {

	private static final Set<String> OFFLINE_PROFILES = Set.of("local", "test");

	private final Environment environment;

	@PostConstruct
	void guardAgainstSilentProduction() {
		List<String> active = List.of(environment.getActiveProfiles());
		if (active.contains("prod")) {
			throw new IllegalStateException(
					"운영 환경에서 메일이 로그로만 남습니다. billage.mail.sender=SES 로 설정하세요.");
		}
		if (active.stream().noneMatch(OFFLINE_PROFILES::contains)) {
			log.warn("메일이 실제로 발송되지 않습니다(LOG 모드). 인증 코드는 로그에만 남습니다. "
					+ "실제 발송이 필요하면 billage.mail.sender=SES 로 설정하세요. activeProfiles={}", active);
		}
	}

	@Override
	public void send(String to, String subject, String body) {
		log.info("[메일 발송 생략 - LOG 모드] to={} subject={}\n{}", to, subject, body);
	}
}
