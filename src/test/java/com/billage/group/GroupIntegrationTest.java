package com.billage.group;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.billage.support.HttpTestClient;
import com.billage.support.HttpTestClient.ListResponse;
import com.billage.support.HttpTestClient.Response;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 모임 생성·조회와 권한 격리 검증.
 * <p>
 * 특히 <b>다른 모임 데이터 접근 차단</b>은 다른 요청 경로로는 확인할 수 없는 보안 규칙이라
 * conventions.md 기준상 통합 테스트로 반드시 확인한다.
 */
class GroupIntegrationTest extends IntegrationTest {

	private static final String PASSWORD = "password123!";

	@LocalServerPort
	int port;

	@Autowired
	UserRepository userRepository;
	@Autowired
	GroupRepository groupRepository;
	@Autowired
	GroupManagerRepository groupManagerRepository;
	@Autowired
	PasswordEncoder passwordEncoder;

	private HttpTestClient http;
	private String ownerToken;
	private String otherToken;
	private User owner;

	@BeforeEach
	void setUp() {
		http = new HttpTestClient(port); // 테이블 정리는 IntegrationTest가 담당한다
		owner = createUser("owner@example.com", "총무");
		createUser("other@example.com", "남");
		ownerToken = login("owner@example.com");
		otherToken = login("other@example.com");
	}

	// --- 생성 ---

	@Test
	void 모임_생성시_생성자가_OWNER_관리자로_등록된다() {
		Response response = createGroup(ownerToken, "테스트모임");

		assertThat(response.status()).isEqualTo(201);
		assertThat(response.at("name")).isEqualTo("테스트모임");
		assertThat(response.at("status")).isEqualTo("ACTIVE");
		assertThat(response.at("myRole")).isEqualTo("OWNER");

		Long groupId = ((Number) response.at("id")).longValue();
		GroupManager manager = groupManagerRepository
				.findByGroupIdAndUserId(groupId, owner.getId()).orElseThrow();
		assertThat(manager.getRole()).isEqualTo(ManagerRole.OWNER);
	}

	@Test
	void 모임_생성시_혼동문자_없는_8자리_초대코드가_발급된다() {
		Response response = createGroup(ownerToken, "테스트모임");

		String inviteCode = (String) response.at("inviteCode");
		assertThat(inviteCode).hasSize(8).matches("[A-HJ-NP-Z2-9]{8}");
	}

	@Test
	void 서로_다른_모임은_서로_다른_초대코드를_받는다() {
		String first = (String) createGroup(ownerToken, "모임A").at("inviteCode");
		String second = (String) createGroup(ownerToken, "모임B").at("inviteCode");

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void 모임명이_비어있으면_400() {
		Response response = createGroup(ownerToken, " ");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.at("code")).isEqualTo("INVALID_REQUEST");
	}

	@Test
	void 모임명이_10자를_넘으면_400() {
		Response response = createGroup(ownerToken, "가나다라마바사아자차카");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.at("code")).isEqualTo("INVALID_REQUEST");
	}

	// --- 권한 격리 (핵심) ---

	@Test
	void 관리자가_아닌_사용자는_모임_상세를_조회할_수_없다() {
		Long groupId = ((Number) createGroup(ownerToken, "남의모임").at("id")).longValue();

		Response response = http.get("/api/v1/groups/" + groupId, otherToken);

		assertThat(response.status()).isEqualTo(403);
		assertThat(response.at("code")).isEqualTo("NOT_GROUP_MANAGER");
	}

	@Test
	void 내_모임_목록에는_내가_관리자인_모임만_보인다() {
		createGroup(ownerToken, "내모임");
		createGroup(otherToken, "남의모임");

		ListResponse response = http.getList("/api/v1/groups", ownerToken);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.body()).hasSize(1);
		assertThat(response.body().getFirst()).containsEntry("name", "내모임");
		assertThat(response.body().getFirst()).containsEntry("myRole", "OWNER");
	}

	@Test
	void 내_모임_목록에는_초대코드를_노출하지_않는다() {
		createGroup(ownerToken, "내모임");

		ListResponse response = http.getList("/api/v1/groups", ownerToken);

		assertThat(response.body().getFirst()).doesNotContainKey("inviteCode");
	}

	@Test
	void 관리자는_자신의_모임_상세를_조회할_수_있다() {
		Long groupId = ((Number) createGroup(ownerToken, "내모임").at("id")).longValue();

		Response response = http.get("/api/v1/groups/" + groupId, ownerToken);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("myRole")).isEqualTo("OWNER");
		assertThat(response.at("inviteCode")).isNotNull();
	}

	// --- 인증 ---

	@Test
	void 인증_없이_모임_API_요청시_401() {
		// 인증 실패 응답 본문은 목록(배열)이 아니라 공통 에러 객체다
		Response list = http.get("/api/v1/groups", null);
		assertThat(list.status()).isEqualTo(401);
		assertThat(list.at("code")).isEqualTo("UNAUTHORIZED");

		Response create = http.postJson("/api/v1/groups", Map.of("name", "모임"), null);
		assertThat(create.status()).isEqualTo(401);
		assertThat(create.at("code")).isEqualTo("UNAUTHORIZED");
	}

	// --- helpers ---

	private User createUser(String email, String name) {
		return userRepository.save(User.create(email, passwordEncoder.encode(PASSWORD), name));
	}

	private String login(String email) {
		return (String) http.postJson("/api/v1/auth/login",
				Map.of("email", email, "password", PASSWORD)).at("accessToken");
	}

	private Response createGroup(String token, String name) {
		return http.postJson("/api/v1/groups", Map.of("name", name), token);
	}
}
