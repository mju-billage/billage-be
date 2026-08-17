package com.billage.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.billage.auth.token.RefreshTokenRepository;
import com.billage.support.HttpTestClient;
import com.billage.support.HttpTestClient.Response;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

class SocialAuthIntegrationTest extends IntegrationTest {

	private static final String GOOGLE_SUB = "google-sub-1";
	private static final String EMAIL = "social@example.com";

	@LocalServerPort
	int port;

	@Autowired
	UserRepository userRepository;
	@Autowired
	SocialAccountRepository socialAccountRepository;
	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@MockitoBean
	GoogleTokenVerifier googleTokenVerifier;

	private HttpTestClient http;

	@BeforeEach
	void setUp() {
		http = new HttpTestClient(port);
		refreshTokenRepository.deleteAll();
		socialAccountRepository.deleteAll();
		userRepository.deleteAll();
		when(googleTokenVerifier.provider()).thenReturn(SocialProvider.GOOGLE);
	}

	@Test
	void 최초_로그인시_가입_필요_응답() {
		when(googleTokenVerifier.verify(anyString())).thenReturn(new OAuthUserInfo(GOOGLE_SUB, EMAIL));

		Response response = socialLogin("valid-google-id-token");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("data.status")).isEqualTo("SIGNUP_REQUIRED");
		assertThat(response.at("data.email")).isEqualTo(EMAIL);
		assertThat(response.at("data.login")).isNull();
	}

	@Test
	void 가입_완료후_즉시_로그인_처리() {
		when(googleTokenVerifier.verify(anyString())).thenReturn(new OAuthUserInfo(GOOGLE_SUB, EMAIL));

		Response response = socialSignup("valid-google-id-token", "홍길동");

		assertThat(response.status()).isEqualTo(201);
		assertThat(response.at("data.user.email")).isEqualTo(EMAIL);
		assertThat(response.at("data.user.name")).isEqualTo("홍길동");
		assertThat(response.at("data.accessToken")).isNotNull();
		assertThat(response.at("data.refreshToken")).isNotNull();

		User created = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(created.getTermsAgreedAt()).isNotNull();
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, GOOGLE_SUB))
				.isPresent();
	}

	@Test
	void 가입후_같은_계정으로_로그인시_바로_토큰_발급() {
		when(googleTokenVerifier.verify(anyString())).thenReturn(new OAuthUserInfo(GOOGLE_SUB, EMAIL));
		socialSignup("valid-google-id-token", "홍길동");

		Response response = socialLogin("valid-google-id-token");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("data.status")).isEqualTo("LOGIN");
		assertThat(response.at("data.login.user.email")).isEqualTo(EMAIL);
		assertThat(response.at("data.login.accessToken")).isNotNull();
	}

	@Test
	void 이미_가입된_계정으로_가입_재요청시_새로_만들지_않고_로그인_처리() {
		when(googleTokenVerifier.verify(anyString())).thenReturn(new OAuthUserInfo(GOOGLE_SUB, EMAIL));
		socialSignup("valid-google-id-token", "홍길동");

		Response response = socialSignup("valid-google-id-token", "홍길동");

		assertThat(response.status()).isEqualTo(201);
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void 유효하지_않은_토큰이면_401() {
		when(googleTokenVerifier.verify(anyString())).thenThrow(
				new com.billage.common.exception.BusinessException(
						com.billage.common.exception.ErrorCode.SOCIAL_TOKEN_INVALID));

		Response response = socialLogin("invalid-token");

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.at("code")).isEqualTo("SOCIAL_TOKEN_INVALID");
	}

	@Test
	void 약관_미동의시_400() {
		Response response = http.postJson("/api/v1/auth/social/signup",
				Map.of("provider", "GOOGLE", "token", "valid-google-id-token", "name", "홍길동", "termsAgreed", false));

		assertThat(response.status()).isEqualTo(400);
	}

	private Response socialLogin(String token) {
		return http.postJson("/api/v1/auth/social/login", Map.of("provider", "GOOGLE", "token", token));
	}

	private Response socialSignup(String token, String name) {
		return http.postJson("/api/v1/auth/social/signup",
				Map.of("provider", "GOOGLE", "token", token, "name", name, "termsAgreed", true));
	}
}
