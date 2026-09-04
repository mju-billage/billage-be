package com.billage.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * LOG 모드는 메일을 보내지 않는다. 운영에서 이 구현이 뜨면 가입자가 인증 코드를 못 받는데
 * 오류도 나지 않으므로, 시작 자체를 막는지 확인한다.
 */
class LogMailSenderTest {

	@Test
	void 운영_프로필에서는_시작을_실패시킨다() {
		MockEnvironment prod = new MockEnvironment();
		prod.setActiveProfiles("prod");

		assertThatThrownBy(() -> new LogMailSender(prod).guardAgainstSilentProduction())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("billage.mail.sender=SES");
	}

	@Test
	void 로컬과_테스트에서는_그대로_뜬다() {
		MockEnvironment local = new MockEnvironment();
		local.setActiveProfiles("local");

		assertThatCode(() -> new LogMailSender(local).guardAgainstSilentProduction())
				.doesNotThrowAnyException();
	}

	@Test
	void 활성_프로필이_없고_기본이_local_이면_본문을_남긴다() {
		// 로컬은 spring.profiles.default: local 로 돌아 활성 프로필 배열이 비어 있다.
		// 활성 목록만 보면 배포 환경으로 오인해 인증 코드를 감춰 버린다.
		MockEnvironment defaulted = new MockEnvironment();
		defaulted.setDefaultProfiles("local");
		LogMailSender sender = new LogMailSender(defaulted);
		sender.guardAgainstSilentProduction();

		assertThatCode(() -> sender.send("a@example.com", "제목", "인증 코드는 123456 입니다."))
				.doesNotThrowAnyException();
		assertThat(sender.leavesBodyInLog()).isTrue();
	}

	@Test
	void 배포_환경에서는_본문을_남기지_않는다() {
		MockEnvironment dev = new MockEnvironment();
		dev.setActiveProfiles("dev");
		LogMailSender sender = new LogMailSender(dev);
		sender.guardAgainstSilentProduction();

		assertThat(sender.leavesBodyInLog()).isFalse();
	}

	@Test
	void dev_에서는_경고만_남기고_뜬다() {
		MockEnvironment dev = new MockEnvironment();
		dev.setActiveProfiles("dev");

		assertThatCode(() -> new LogMailSender(dev).guardAgainstSilentProduction())
				.doesNotThrowAnyException();
	}
}
