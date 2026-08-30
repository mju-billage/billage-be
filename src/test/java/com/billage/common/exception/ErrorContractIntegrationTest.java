package com.billage.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.billage.support.HttpTestClient;
import com.billage.support.HttpTestClient.Response;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 공통 에러 계약을 <b>실제 MVC 경로</b>로 검증한다.
 *
 * <p>{@link GlobalExceptionHandlerTest} 는 예외 타입 → 상태코드 매핑만 본다. 아래 두 가지는 매핑이 맞아도
 * 실제로 그 예외가 던져지지 않으면 의미가 없어서 — 미매핑 경로는 Security 필터를 통과한 뒤에야 예외가 나고,
 * 업로드 상한은 요청을 파싱하는 단계에서 갈린다 — 컨테이너를 띄워 확인한다.
 */
class ErrorContractIntegrationTest extends IntegrationTest {

	private static final String EMAIL = "error-contract@example.com";
	private static final String PASSWORD = "password123!";

	/** 컨테이너 상한(spring.servlet.multipart.max-file-size = 12MB)은 넘고 요청 상한(24MB)은 넘지 않는 크기. */
	private static final int OVER_CONTAINER_LIMIT_BYTES = 13 * 1024 * 1024;

	@LocalServerPort
	int port;

	@Autowired
	UserRepository userRepository;
	@Autowired
	PasswordEncoder passwordEncoder;

	private HttpTestClient http;
	private String accessToken;

	@BeforeEach
	void setUp() {
		http = new HttpTestClient(port);
		userRepository.save(User.create(EMAIL, passwordEncoder.encode(PASSWORD), "홍길동"));
		accessToken = (String) http.postJson("/api/v1/auth/login",
				Map.of("email", EMAIL, "password", PASSWORD)).at("data.tokens.accessToken");
	}

	@Test
	@DisplayName("인증된 요청이 존재하지 않는 경로로 가면 404 — 500 으로 새지 않는다")
	void unmappedPathReturnsNotFound() {
		Response response = http.get("/api/v1/users/me", accessToken);

		assertThat(response.status()).isEqualTo(404);
		assertThat(response.at("code")).isEqualTo("RESOURCE_NOT_FOUND");
	}

	@Test
	@DisplayName("인증 없이 존재하지 않는 경로로 가면 401 — 경로 존재 여부를 흘리지 않는다")
	void unmappedPathWithoutTokenReturnsUnauthorized() {
		Response response = http.get("/api/v1/users/me", null);

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.at("code")).isEqualTo("UNAUTHORIZED");
	}

	@Test
	@DisplayName("컨테이너 업로드 상한을 넘으면 413 — FileService 정책 상한과 같은 코드로 응답한다")
	void oversizedUploadReturnsPayloadTooLarge() {
		Response response = http.postMultipartFile("/api/v1/files", "file", "huge.jpg", "image/jpeg",
				new byte[OVER_CONTAINER_LIMIT_BYTES], Map.of("purpose", "RECEIPT"), accessToken);

		assertThat(response.status()).isEqualTo(413);
		assertThat(response.at("code")).isEqualTo("FILE_SIZE_EXCEEDED");
	}

	@Test
	@DisplayName("허용되지 않은 메서드는 405")
	void methodNotAllowed() {
		Response response = http.delete("/api/v1/auth/login", accessToken);

		assertThat(response.status()).isEqualTo(405);
		assertThat(response.at("code")).isEqualTo("METHOD_NOT_ALLOWED");
	}
}
