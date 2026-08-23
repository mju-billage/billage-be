package com.billage.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
		user = userRepository.save(User.create(EMAIL, passwordEncoder.encode(PASSWORD), "홍길동"));
	}

	// --- 회원가입 ---

	@Test
	void 회원가입_성공() {
		Response response = signup("new@example.com", "Password123!", "김가입");

		assertThat(response.status()).isEqualTo(201);
		assertThat(response.at("data.userId")).isNotNull();
		assertThat(response.at("data.email")).isEqualTo("new@example.com");
		assertThat(response.at("data.name")).isEqualTo("김가입");
		// 명세상 가입과 로그인은 분리돼 있다 — 토큰을 함께 내려주지 않는다.
		assertThat(response.at("data.accessToken")).isNull();
		assertThat(response.at("data.refreshToken")).isNull();
	}

	@Test
	void 회원가입_후_해당_계정으로_로그인할_수_있다() {
		signup("new@example.com", "Password123!", "김가입");

		Response response = login("new@example.com", "Password123!");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("data.user.email")).isEqualTo("new@example.com");
	}

	@Test
	void 회원가입_비밀번호는_평문으로_저장되지_않는다() {
		signup("new@example.com", "Password123!", "김가입");

		String stored = userRepository.findByEmail("new@example.com").orElseThrow().getPassword();

		assertThat(stored).isNotEqualTo("Password123!");
		assertThat(passwordEncoder.matches("Password123!", stored)).isTrue();
	}

	@Test
	void 회원가입_실패_이미_가입된_이메일() {
		Response response = signup(EMAIL, "Password123!", "김가입");

		assertThat(response.status()).isEqualTo(409);
		assertThat(response.at("code")).isEqualTo("EMAIL_ALREADY_EXISTS");
	}

	@Test
	void 회원가입_실패_소셜_전용_계정이_점유한_이메일() {
		userRepository.save(User.createSocial("social@example.com", "소셜", LocalDateTime.now()));

		Response response = signup("social@example.com", "Password123!", "김가입");

		assertThat(response.status()).isEqualTo(409);
		assertThat(response.at("code")).isEqualTo("EMAIL_ALREADY_EXISTS");
	}

	@Test
	void 회원가입_실패_비밀번호_규칙_위반() {
		// 각각 소문자만 / 특수문자 없음 / 숫자 없음 / 8자 미만
		for (String weak : new String[] {"password", "Password123", "Password!", "Pw1!"}) {
			Response response = signup("new@example.com", weak, "김가입");

			assertThat(response.status()).as("비밀번호 %s", weak).isEqualTo(400);
			assertThat(response.at("code")).isEqualTo("INVALID_REQUEST");
		}
		assertThat(userRepository.findByEmail("new@example.com")).isEmpty();
	}

	@Test
	void 회원가입_실패_이름이_10자를_넘음() {
		Response response = signup("new@example.com", "Password123!", "가나다라마바사아자차카");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.at("code")).isEqualTo("INVALID_REQUEST");
	}

	@Test
	void 회원가입_비밀번호_72바이트까지_허용된다() {
		// BCrypt 한계와 같은 값이 경계다. 한글은 글자당 3바이트 — 4 + (3 * 22) + 2 = 72바이트.
		String boundary = "Aa1!" + "가".repeat(22) + "xy";
		assertThat(boundary.getBytes(StandardCharsets.UTF_8)).hasSize(72);

		Response response = signup("new@example.com", boundary, "김가입");

		assertThat(response.status()).isEqualTo(201);
	}

	@Test
	void 회원가입_실패_비밀번호가_72바이트를_넘음() {
		// 글자 수(27)로는 72 이하라 @Size 로는 못 막는다 — BCrypt 가 예외를 던져 500 이 되던 케이스.
		String tooLong = "Aa1!" + "한".repeat(23);
		assertThat(tooLong.length()).isLessThanOrEqualTo(72);
		assertThat(tooLong.getBytes(StandardCharsets.UTF_8)).hasSize(73);

		Response response = signup("new@example.com", tooLong, "김가입");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.at("code")).isEqualTo("INVALID_REQUEST");
		assertThat(userRepository.findByEmail("new@example.com")).isEmpty();
	}

	@Test
	void 회원가입_실패_이메일이_254자를_넘음() {
		// 형식은 유효하지만 users.email VARCHAR(255) 를 넘겨 저장 단계에서 500 이 되던 케이스.
		String longEmail = "a".repeat(250) + "@example.com";
		assertThat(longEmail.length()).isGreaterThan(254);

		Response response = signup(longEmail, "Password123!", "김가입");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.at("code")).isEqualTo("INVALID_REQUEST");
	}

	@Test
	void 회원가입_동시_요청은_하나만_성공하고_나머지는_409() throws Exception {
		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			List<Callable<Integer>> tasks = Collections.nCopies(threads,
					() -> signup("race@example.com", "Password123!", "김가입").status());

			List<Integer> statuses = executor.invokeAll(tasks).stream()
					.map(future -> {
						try {
							return future.get();
						} catch (Exception e) {
							throw new IllegalStateException(e);
						}
					})
					.toList();

			// UNIQUE 제약에서 밀린 요청이 500 이 아니라 409 로 나와야 한다.
			assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
			assertThat(statuses).allMatch(status -> status == 201 || status == 409);
			assertThat(userRepository.findByEmail("race@example.com")).isPresent();
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 회원가입_실패_이메일_형식_오류() {
		Response response = signup("not-an-email", "Password123!", "김가입");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.at("code")).isEqualTo("INVALID_REQUEST");
	}

	// --- 로그인 ---

	@Test
	void 로그인_성공() {
		Response response = login(EMAIL, PASSWORD);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("data.user.email")).isEqualTo(EMAIL);
		assertThat(response.at("data.user.name")).isEqualTo("홍길동");
		assertThat(response.at("data.accessToken")).isNotNull();
		assertThat(response.at("data.refreshToken")).isNotNull();
		assertThat(response.at("data.tokenType")).isEqualTo("Bearer");
		assertThat(response.at("data.accessTokenExpiresIn")).isEqualTo(1800);
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
		String accessToken = (String) login(EMAIL, PASSWORD).at("data.accessToken");

		Response response = http.get("/api/v1/auth/me", accessToken);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("data.id")).isEqualTo(user.getId().intValue());
		assertThat(response.at("data.email")).isEqualTo(EMAIL);
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
		// 리소스 서버가 자체 EntryPoint 를 쓰더라도 공통 에러 형식이 유지되어야 한다
		assertThat(response.at("code")).isEqualTo("UNAUTHORIZED");
	}

	// --- 재발급 ---

	@Test
	void 정상적인_Refresh_Token_재발급() {
		String originalRefresh = (String) login(EMAIL, PASSWORD).at("data.refreshToken");

		Response response = refresh(originalRefresh);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("data.accessToken")).isNotNull();
		assertThat(response.at("data.tokenType")).isEqualTo("Bearer");
		String newRefresh = (String) response.at("data.refreshToken");
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
		String refreshToken = (String) login(EMAIL, PASSWORD).at("data.refreshToken");
		logout(refreshToken);

		Response response = refresh(refreshToken);

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.at("code")).isEqualTo("REFRESH_TOKEN_REUSED");
	}

	// --- 재사용 감지 ---

	@Test
	void Rotation된_토큰_재사용시_패밀리_전체_폐기() {
		String originalRefresh = (String) login(EMAIL, PASSWORD).at("data.refreshToken");
		String tokenHash = tokenHasher.hash(originalRefresh);
		String familyId = refreshTokenRepository.findAll().stream()
				.filter(t -> t.getTokenHash().equals(tokenHash))
				.findFirst().orElseThrow().getFamilyId();

		// 1차 재발급 → 원본 토큰은 회전(ROTATED)됨
		String rotatedRefresh = (String) refresh(originalRefresh).at("data.refreshToken");

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
		String refreshToken = (String) login(EMAIL, PASSWORD).at("data.refreshToken");

		assertThat(logout(refreshToken).status()).isEqualTo(204); // 1회
		assertThat(logout(refreshToken).status()).isEqualTo(204); // 이미 폐기됨
		assertThat(logout("never-existed-token").status()).isEqualTo(204); // 미존재
	}

	// --- helpers ---

	private Response signup(String email, String password, String name) {
		return http.postJson("/api/v1/auth/signup",
				Map.of("email", email, "password", password, "name", name));
	}

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
