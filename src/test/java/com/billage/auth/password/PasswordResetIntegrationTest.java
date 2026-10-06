package com.billage.auth.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.mail.MailSender;
import com.billage.support.HttpTestClient;
import com.billage.support.HttpTestClient.Response;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 비밀번호 재설정(화면 COM-2-PAGE-02-0). 임시 비밀번호가 메일로 나가고 그 값으로만 로그인되는지,
 * 그리고 가입 여부가 응답으로 드러나지 않는지 확인한다.
 */
@Import(PasswordResetIntegrationTest.RecordingMailConfig.class)
class PasswordResetIntegrationTest extends IntegrationTest {

	private static final String EMAIL = "member@example.com";
	private static final String PASSWORD = "Password123!";

	@LocalServerPort
	int port;

	@Autowired
	UserRepository userRepository;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	RecordingMailSender mailSender;
	@Autowired
	PasswordResetProperties properties;
	@Autowired
	PlatformTransactionManager transactionManager;

	private HttpTestClient http;

	@BeforeEach
	void setUp() {
		http = new HttpTestClient(port);
		mailSender.reset();
		userRepository.save(User.create(EMAIL, passwordEncoder.encode(PASSWORD), "홍길동"));
	}

	@Test
	void 임시_비밀번호를_보내고_그_값으로_로그인된다() {
		Response response = reset(EMAIL);

		assertThat(response.status()).isEqualTo(204);
		assertThat(mailSender.recipients()).containsExactly(EMAIL);
		assertThat(login(EMAIL, mailSender.lastPassword()).status()).isEqualTo(200);
	}

	@Test
	void 기존_비밀번호로는_더_이상_로그인되지_않는다() {
		reset(EMAIL);

		Response response = login(EMAIL, PASSWORD);

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.at("code")).isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	void 임시_비밀번호는_비밀번호_변경의_현재_비밀번호로_쓸_수_있다() {
		reset(EMAIL);
		String temporary = mailSender.lastPassword();
		String accessToken = (String) login(EMAIL, temporary).at("data.tokens.accessToken");

		Response response = http.patchJson("/api/v1/auth/password",
				Map.of("currentPassword", temporary, "newPassword", "NewPassword456!"), accessToken);

		assertThat(response.status()).isEqualTo(204);
		assertThat(login(EMAIL, "NewPassword456!").status()).isEqualTo(200);
	}

	@Test
	void 로그인해_있던_기기는_모두_끊긴다() {
		String refreshToken = (String) login(EMAIL, PASSWORD).at("data.tokens.refreshToken");

		reset(EMAIL);

		Response response = http.postJson("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));
		assertThat(response.status()).isEqualTo(401);
	}

	@Test
	void 가입되지_않은_주소도_같은_응답을_주고_메일은_보내지_않는다() {
		Response response = reset("nobody@example.com");

		assertThat(response.status()).isEqualTo(204);
		assertThat(mailSender.recipients()).isEmpty();
	}

	@Test
	void 소셜_전용_계정은_같은_응답을_주고_비밀번호를_만들지_않는다() {
		userRepository.save(User.createSocial("social@example.com", "소셜", LocalDateTime.now()));

		Response response = reset("social@example.com");

		assertThat(response.status()).isEqualTo(204);
		assertThat(mailSender.recipients()).isEmpty();
		assertThat(userRepository.findByEmail("social@example.com").orElseThrow().hasPassword()).isFalse();
	}

	@Test
	void 발송_횟수를_넘기면_응답은_같지만_비밀번호를_바꾸지_않는다() {
		for (int i = 0; i < properties.maxSends(); i++) {
			reset(EMAIL);
		}
		String lastValid = mailSender.lastPassword();

		Response response = reset(EMAIL);

		assertThat(response.status()).isEqualTo(204);
		assertThat(mailSender.recipients()).hasSize(properties.maxSends());
		assertThat(login(EMAIL, lastValid).status()).isEqualTo(200);
	}

	@Test
	void 메일_발송에_실패하면_기존_비밀번호가_그대로_남는다() {
		mailSender.failNext();

		Response response = reset(EMAIL);

		assertThat(response.status()).isEqualTo(500);
		assertThat(response.at("code")).isEqualTo("MAIL_SEND_FAILED");
		assertThat(login(EMAIL, PASSWORD).status()).isEqualTo(200);
	}

	@Test
	void 재설정과_겹친_프로필_수정이_임시_비밀번호를_되돌리지_않는다() throws Exception {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		// 프로필 수정이 계정을 읽어 둔 사이에 재설정이 끝나고, 그 뒤에 수정이 커밋되는 순서다.
		tx.executeWithoutResult(status -> {
			User loaded = userRepository.findByEmail(EMAIL).orElseThrow();
			try {
				CompletableFuture.runAsync(() -> reset(EMAIL)).get(20, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
			loaded.changeName("새이름");
		});

		User saved = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(saved.getName()).isEqualTo("새이름");
		assertThat(login(EMAIL, mailSender.lastPassword()).status()).isEqualTo(200);
		assertThat(saved.getPasswordResetCount()).isEqualTo(1);
	}

	@Test
	void 이메일_형식이_아니면_거부한다() {
		Response response = reset("not-an-email");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.at("code")).isEqualTo("INVALID_REQUEST");
	}

	private Response reset(String email) {
		return http.postJson("/api/v1/auth/password/reset", Map.of("email", email));
	}

	private Response login(String email, String password) {
		return http.postJson("/api/v1/auth/login", Map.of("email", email, "password", password));
	}

	/** 발송된 본문을 붙잡아 두는 테스트용 발송기. 실제 메일은 보내지 않는다. */
	static class RecordingMailSender implements MailSender {

		private static final Pattern PASSWORD_LINE = Pattern.compile("임시 비밀번호는 (\\S+) 입니다");

		private final List<String> recipients = new ArrayList<>();
		private final List<String> bodies = new ArrayList<>();
		private boolean failNext;

		@Override
		public synchronized void send(String to, String subject, String body) {
			if (failNext) {
				failNext = false;
				throw new BusinessException(ErrorCode.MAIL_SEND_FAILED);
			}
			recipients.add(to);
			bodies.add(body);
		}

		synchronized void reset() {
			recipients.clear();
			bodies.clear();
			failNext = false;
		}

		synchronized void failNext() {
			failNext = true;
		}

		synchronized List<String> recipients() {
			return List.copyOf(recipients);
		}

		synchronized String lastPassword() {
			Matcher matcher = PASSWORD_LINE.matcher(bodies.get(bodies.size() - 1));
			if (!matcher.find()) {
				throw new IllegalStateException("본문에서 임시 비밀번호를 찾지 못했습니다.");
			}
			return matcher.group(1);
		}
	}

	@TestConfiguration
	static class RecordingMailConfig {

		@Bean
		@Primary
		RecordingMailSender passwordResetRecordingMailSender() {
			return new RecordingMailSender();
		}
	}
}
