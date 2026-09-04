package com.billage.common.mail;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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

	/**
	 * 실제 사용자가 없는 환경. {@code acceptsProfiles} 로 판정해야 한다 —
	 * 로컬은 {@code spring.profiles.default: local} 로 도는 경우가 많아 <b>활성 프로필 배열이 비어 있고</b>,
	 * 활성 목록만 보면 로컬을 배포 환경으로 오인해 인증 코드를 로그에서 감춰 버린다.
	 */
	private static final Profiles OFFLINE = Profiles.of("local", "test");
	private static final Profiles PRODUCTION = Profiles.of("prod");

	private final Environment environment;

	/** 로컬·테스트처럼 실제 사용자가 없는 환경. 이때만 본문(인증 코드)을 로그에 남긴다. */
	private boolean offline;

	/** 인증 코드가 로그에 남는 환경인지. */
	boolean leavesBodyInLog() {
		return offline;
	}

	@PostConstruct
	void guardAgainstSilentProduction() {
		this.offline = environment.acceptsProfiles(OFFLINE);
		if (environment.acceptsProfiles(PRODUCTION)) {
			throw new IllegalStateException(
					"운영 환경에서 메일이 로그로만 남습니다. billage.mail.sender=SES 로 설정하세요.");
		}
		if (!offline) {
			log.warn("메일이 실제로 발송되지 않습니다(LOG 모드). 실제 발송이 필요하면 "
					+ "billage.mail.sender=SES 로 설정하세요. activeProfiles={}",
					List.of(environment.getActiveProfiles()));
		}
	}

	/**
	 * 로컬·테스트에서만 본문을 남긴다. 본문에는 인증 코드가 들어 있어, 배포 환경 로그에 찍히면
	 * 로그를 볼 수 있는 사람이 남의 가입을 가로챌 수 있다.
	 */
	@Override
	public void send(String to, String subject, String body) {
		if (offline) {
			log.info("[메일 발송 생략 - LOG 모드] to={} subject={}\n{}", to, subject, body);
			return;
		}
		log.warn("[메일 발송 생략 - LOG 모드] to={} subject={} (본문은 남기지 않습니다)", to, subject);
	}
}
