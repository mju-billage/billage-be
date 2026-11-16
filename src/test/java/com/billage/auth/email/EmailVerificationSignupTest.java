package com.billage.auth.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.billage.common.mail.MailSender;
import com.billage.support.HttpTestClient;
import com.billage.support.HttpTestClient.Response;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 이메일 인증과 가입의 연결. 인증 요구를 켠 상태에서, 인증이 5분 안의 것일 때만 가입되는지 확인한다.
 * 토큰을 주고받지 않고 서버가 그 주소의 인증 상태를 직접 본다.
 */
@TestPropertySource(properties = "billage.auth.email-verification.required-for-signup=true")
@Import(EmailVerificationSignupTest.RecordingMailConfig.class)
class EmailVerificationSignupTest extends IntegrationTest {

	private static final String EMAIL = "new@example.com";

	@LocalServerPort
	int port;

	@Autowired
	RecordingMailSender mailSender;
	@Autowired
	UserRepository userRepository;
	@Autowired
	JdbcTemplate jdbcTemplate;

	private HttpTestClient http;

	@BeforeEach
	void setUp() {
		http = new HttpTestClient(port);
		mailSender.reset();
	}

	@Test
	void 인증을_마치고_바로_가입하면_된다() {
		verify(EMAIL);

		assertThat(signup(EMAIL).status()).isEqualTo(201);
	}

	@Test
	void 인증하지_않은_주소로는_가입할_수_없다() {
		Response response = signup(EMAIL);

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.at("code")).isEqualTo("EMAIL_NOT_VERIFIED");
	}

	@Test
	void 코드만_받고_확인하지_않았으면_가입할_수_없다() {
		send(EMAIL);

		assertThat(signup(EMAIL).at("code")).isEqualTo("EMAIL_NOT_VERIFIED");
	}

	@Test
	void 인증한_지_5분이_지나면_다시_인증해야_한다() {
		verify(EMAIL);
		jdbcTemplate.update(
				"update email_verification set verified_at = date_sub(verified_at, interval 301 second) where email = ?",
				EMAIL);

		Response response = signup(EMAIL);

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.at("code")).isEqualTo("EMAIL_NOT_VERIFIED");
		assertThat(userRepository.findByEmail(EMAIL)).isEmpty();
	}

	@Test
	void 인증한_지_5분이_안_됐으면_가입된다() {
		verify(EMAIL);
		jdbcTemplate.update(
				"update email_verification set verified_at = date_sub(verified_at, interval 240 second) where email = ?",
				EMAIL);

		assertThat(signup(EMAIL).status()).isEqualTo(201);
	}

	@Test
	void 기한이_지난_뒤_다시_인증하면_가입된다() {
		verify(EMAIL);
		jdbcTemplate.update(
				"update email_verification set verified_at = date_sub(verified_at, interval 301 second) where email = ?",
				EMAIL);

		verify(EMAIL);

		assertThat(signup(EMAIL).status()).isEqualTo(201);
	}

	/** 화면은 이 응답으로 "이미 가입된 이메일"을 알린다. 별도의 중복 확인 API 는 두지 않는다. */
	@Test
	void 이미_가입된_주소에_인증_코드를_요청하면_409() {
		userRepository.save(User.create(EMAIL, "encoded", "기존회원"));

		Response response = send(EMAIL);

		assertThat(response.status()).isEqualTo(409);
		assertThat(response.at("code")).isEqualTo("EMAIL_ALREADY_EXISTS");
		assertThat(mailSender.bodies()).isEmpty();
	}

	private void verify(String email) {
		send(email);
		Response confirm = http.postJson("/api/v1/auth/email-verifications/confirm",
				Map.of("email", email, "code", mailSender.lastCode()));
		assertThat(confirm.status()).isEqualTo(200);
	}

	private Response send(String email) {
		return http.postJson("/api/v1/auth/email-verifications", Map.of("email", email));
	}

	private Response signup(String email) {
		return http.postJson("/api/v1/auth/signup",
				Map.of("email", email, "password", "Password123!", "name", "김가입"));
	}

	/** 발송된 본문을 붙잡아 두는 테스트용 발송기. 실제 메일은 보내지 않는다. */
	static class RecordingMailSender implements MailSender {

		private static final Pattern CODE = Pattern.compile("(\\d{6})");

		private final List<String> bodies = new ArrayList<>();

		@Override
		public synchronized void send(String to, String subject, String body) {
			bodies.add(body);
		}

		synchronized void reset() {
			bodies.clear();
		}

		synchronized List<String> bodies() {
			return List.copyOf(bodies);
		}

		synchronized String lastCode() {
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
		RecordingMailSender signupRecordingMailSender() {
			return new RecordingMailSender();
		}
	}
}
