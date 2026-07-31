package com.billage.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.billage.auth.token.RefreshToken;
import com.billage.auth.token.RefreshTokenRepository;
import com.billage.auth.token.RevokeReason;
import com.billage.auth.token.TokenHasher;
import com.billage.support.HttpTestClient;
import com.billage.support.HttpTestClient.Response;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

class AuthIntegrationTest extends IntegrationTest {

	private static final String EMAIL = "member@example.com";
	private static final String PASSWORD = "password123!";

	@LocalServerPort
	int port;

	@Autowired
	UserRepository userRepository;
	@Autowired
	RefreshTokenRepository refreshTokenRepository;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	TokenHasher tokenHasher;

	private HttpTestClient http;
	private User user;

	@BeforeEach
	void setUp() {
		http = new HttpTestClient(port);
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
		user = userRepository.save(User.create(EMAIL, passwordEncoder.encode(PASSWORD), "홍길동"));
	}

	// --- 로그인 ---

	@Test
	void 로그인_성공() {
		Response response = login(EMAIL, PASSWORD);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("user.email")).isEqualTo(EMAIL);
		assertThat(response.at("user.name")).isEqualTo("홍길동");
		assertThat(response.at("accessToken")).isNotNull();
		assertThat(response.at("refreshToken")).isNotNull();
		assertThat(response.at("tokenType")).isEqualTo("Bearer");
		assertThat(response.at("accessTokenExpiresIn")).isEqualTo(1800);
	}

	@Test
	void 로그인_실패_비밀번호_불일치() {
		Response response = login(EMAIL, "wrong-password");

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.at("code")).isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	void 로그인_실패_없는_사용자() {
		Response response = login("nobody@example.com", PASSWORD);

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.at("code")).isEqualTo("INVALID_CREDENTIALS");
	}

	// --- 현재 사용자 / 보호 API ---

	@Test
	void me_인증된_사용자_조회() {
		String accessToken = (String) login(EMAIL, PASSWORD).at("accessToken");

		Response response = http.get("/api/v1/auth/me", accessToken);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("id")).isEqualTo(user.getId().intValue());
		assertThat(response.at("email")).isEqualTo(EMAIL);
	}

	@Test
	void 인증_없이_보호_API_요청시_401() {
		Response response = http.get("/api/v1/auth/me", null);

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.at("code")).isEqualTo("UNAUTHORIZED");
	}

	@Test
	void 유효하지_않은_토큰으로_보호_API_요청시_401() {
		Response response = http.get("/api/v1/auth/me", "not-a-real-jwt");

		assertThat(response.status()).isEqualTo(401);
	}

	// --- 재발급 ---

	@Test
	void 정상적인_Refresh_Token_재발급() {
		String originalRefresh = (String) login(EMAIL, PASSWORD).at("refreshToken");

		Response response = refresh(originalRefresh);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("accessToken")).isNotNull();
		assertThat(response.at("tokenType")).isEqualTo("Bearer");
		String newRefresh = (String) response.at("refreshToken");
		// Rotation: 새 Refresh Token 은 이전과 달라야 한다
		assertThat(newRefresh).isNotNull().isNotEqualTo(originalRefresh);
	}

	@Test
	void 만료된_Refresh_Token_거부() {
		String rawToken = "expired-raw-token-" + UUID.randomUUID();
		refreshTokenRepository.save(RefreshToken.issue(
				user, tokenHasher.hash(rawToken), UUID.randomUUID().toString(), null,
				LocalDateTime.now().minusDays(1)));

		Response response = refresh(rawToken);

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.at("code")).isEqualTo("REFRESH_TOKEN_EXPIRED");
	}

	@Test
	void 존재하지_않는_Refresh_Token_거부() {
		Response response = refresh("does-not-exist");

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.at("code")).isEqualTo("REFRESH_TOKEN_INVALID");
	}

	@Test
	void 로그아웃으로_폐기된_Refresh_Token_거부() {
		String refreshToken = (String) login(EMAIL, PASSWORD).at("refreshToken");
		logout(refreshToken);

		Response response = refresh(refreshToken);

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.at("code")).isEqualTo("REFRESH_TOKEN_REUSED");
	}

	// --- 재사용 감지 ---

	@Test
	void Rotation된_토큰_재사용시_패밀리_전체_폐기() {
		String originalRefresh = (String) login(EMAIL, PASSWORD).at("refreshToken");
		String tokenHash = tokenHasher.hash(originalRefresh);
		String familyId = refreshTokenRepository.findAll().stream()
				.filter(t -> t.getTokenHash().equals(tokenHash))
				.findFirst().orElseThrow().getFamilyId();

		// 1차 재발급 → 원본 토큰은 회전(ROTATED)됨
		String rotatedRefresh = (String) refresh(originalRefresh).at("refreshToken");

		// 이미 회전된 원본 토큰을 재사용 → REUSED
		Response reuse = refresh(originalRefresh);
		assertThat(reuse.status()).isEqualTo(401);
		assertThat(reuse.at("code")).isEqualTo("REFRESH_TOKEN_REUSED");

		// 재사용 후 발급됐던 새 토큰까지 패밀리 전체가 폐기되어 더는 재발급 불가
		assertThat(refresh(rotatedRefresh).status()).isEqualTo(401);

		// 같은 familyId의 모든 토큰이 폐기됐는지 직접 확인
		boolean allRevoked = refreshTokenRepository.findAll().stream()
				.filter(t -> t.getFamilyId().equals(familyId))
				.allMatch(RefreshToken::isRevoked);
		assertThat(allRevoked).isTrue();
		boolean anyReused = refreshTokenRepository.findAll().stream()
				.anyMatch(t -> t.getRevokeReason() == RevokeReason.REUSED);
		assertThat(anyReused).isTrue();
	}

	// --- 로그아웃 멱등성 ---

	@Test
	void 로그아웃_멱등성() {
		String refreshToken = (String) login(EMAIL, PASSWORD).at("refreshToken");

		assertThat(logout(refreshToken).status()).isEqualTo(204); // 1회
		assertThat(logout(refreshToken).status()).isEqualTo(204); // 이미 폐기됨
		assertThat(logout("never-existed-token").status()).isEqualTo(204); // 미존재
	}

	// --- helpers ---

	private Response login(String email, String password) {
		return http.postJson("/api/v1/auth/login", Map.of("email", email, "password", password));
	}

	private Response refresh(String refreshToken) {
		return http.postJson("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));
	}

	private Response logout(String refreshToken) {
		return http.postJson("/api/v1/auth/logout", Map.of("refreshToken", refreshToken));
	}
}
