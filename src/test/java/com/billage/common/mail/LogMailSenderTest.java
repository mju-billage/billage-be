package com.billage.common.mail;

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
	void dev_에서는_경고만_남기고_뜬다() {
		MockEnvironment dev = new MockEnvironment();
		dev.setActiveProfiles("dev");

		assertThatCode(() -> new LogMailSender(dev).guardAgainstSilentProduction())
				.doesNotThrowAnyException();
	}
}
