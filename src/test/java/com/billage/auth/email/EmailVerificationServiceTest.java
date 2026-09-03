package com.billage.auth.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.mail.MailSender;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 이메일 인증(COM-4-PAGE-01-0). 코드는 해시로만 저장하므로 테스트는 발송된 메일 본문에서 코드를 읽는다 —
 * 실제 사용자가 메일로만 코드를 알 수 있다는 것과 같은 조건이다.
 */
@Import(EmailVerificationServiceTest.RecordingMailConfig.class)
class EmailVerificationServiceTest extends IntegrationTest {

	private static final String EMAIL = "new@example.com";

	@Autowired
	EmailVerificationService emailVerificationService;
	@Autowired
	EmailVerificationRepository repository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	RecordingMailSender mailSender;

	@Test
	void 발송한_코드로_인증하면_인증_완료로_남는다() {
		emailVerificationService.send(EMAIL);

		var result = emailVerificationService.confirm(EMAIL, mailSender.lastCode());

		assertThat(result.verified()).isTrue();
		assertThat(repository.findByEmail(EMAIL)).get()
				.satisfies(v -> assertThat(v.isVerified()).isTrue());
	}

	@Test
	void 대소문자와_공백이_달라도_같은_이메일로_본다() {
		emailVerificationService.send("  NEW@Example.com ");

		var result = emailVerificationService.confirm(EMAIL, mailSender.lastCode());

		assertThat(result.email()).isEqualTo(EMAIL);
	}

	@Test
	void 코드가_틀리면_인증되지_않는다() {
		emailVerificationService.send(EMAIL);
		String wrong = mailSender.lastCode().equals("000000") ? "111111" : "000000";

		assertThatThrownBy(() -> emailVerificationService.confirm(EMAIL, wrong))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_CODE_MISMATCH);
	}

	@Test
	void 시도_횟수를_넘기면_코드가_맞아도_막힌다() {
		emailVerificationService.send(EMAIL);
		String code = mailSender.lastCode();
		String wrong = code.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> emailVerificationService.confirm(EMAIL, wrong))
					.isInstanceOf(BusinessException.class);
		}

		assertThatThrownBy(() -> emailVerificationService.confirm(EMAIL, code))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);
	}

	@Test
	void 만료된_코드로는_인증할_수_없다() {
		LocalDateTime now = LocalDateTime.now();
		repository.save(EmailVerification.issue(EMAIL, passwordEncoder.encode("123456"),
				now.minusSeconds(1), now));

		assertThatThrownBy(() -> emailVerificationService.confirm(EMAIL, "123456"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);
	}

	@Test
	void 재전송하면_이전_코드는_무효가_된다() {
		emailVerificationService.send(EMAIL);
		String first = mailSender.lastCode();

		emailVerificationService.send(EMAIL);
		String second = mailSender.lastCode();

		// 6자리 난수라 두 코드가 같을 수 있다. 그때는 무효화 여부를 검증할 수 없으므로 건너뛴다.
		if (!first.equals(second)) {
			assertThatThrownBy(() -> emailVerificationService.confirm(EMAIL, first))
					.isInstanceOf(BusinessException.class)
					.extracting(e -> ((BusinessException) e).getErrorCode())
					.isEqualTo(ErrorCode.VERIFICATION_CODE_MISMATCH);
		}
		assertThat(emailVerificationService.confirm(EMAIL, second).verified()).isTrue();
	}

	@Test
	void 발송_횟수를_넘기면_거절한다() {
		for (int i = 0; i < 5; i++) {
			emailVerificationService.send(EMAIL);
		}

		assertThatThrownBy(() -> emailVerificationService.send(EMAIL))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_SEND_LIMIT_EXCEEDED);
	}

	@Test
	void 이미_가입된_이메일에는_코드를_보내지_않는다() {
		userRepository.save(User.create(EMAIL, "encoded", "기존회원"));

		assertThatThrownBy(() -> emailVerificationService.send(EMAIL))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
		assertThat(mailSender.sent()).isEmpty();
	}

	@Test
	void 코드를_받은_적_없으면_인증할_수_없다() {
		assertThatThrownBy(() -> emailVerificationService.confirm(EMAIL, "123456"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.VERIFICATION_NOT_FOUND);
	}

	/** 발송된 본문을 붙잡아 두는 테스트용 발송기. 실제 메일은 보내지 않는다. */
	static class RecordingMailSender implements MailSender {

		private static final Pattern CODE = Pattern.compile("(\\d{6})");

		private final List<String> bodies = new ArrayList<>();

		@Override
		public void send(String to, String subject, String body) {
			bodies.add(body);
		}

		List<String> sent() {
			return bodies;
		}

		String lastCode() {
			Matcher matcher = CODE.matcher(bodies.get(bodies.size() - 1));
			if (!matcher.find()) {
				throw new IllegalStateException("본문에서 인증 코드를 찾지 못했습니다.");
			}
			return matcher.group(1);
		}
	}

	@TestConfiguration
	static class RecordingMailConfig {

		@Bean
		@Primary
		RecordingMailSender recordingMailSender() {
			return new RecordingMailSender();
		}
	}
}
