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
import com.billage.support.HttpTestClient.Response;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 모임 리소스 접근 제어. 다른 요청 경로로는 검증할 수 없는 보안 규칙이므로 API 레벨로 확인한다.
 */
class GroupAccessIntegrationTest extends IntegrationTest {

	private static final String PASSWORD = "password123!";

	@LocalServerPort
	int port;

	@Autowired
	UserRepository userRepository;
	@Autowired
	PasswordEncoder passwordEncoder;

	private HttpTestClient http;
	private String ownerToken;
	private String adminToken;
	private String outsiderToken;
	private long groupId;

	@BeforeEach
	void setUp() {
		http = new HttpTestClient(port);

		ownerToken = tokenOf("owner@example.com", "총무");
		adminToken = tokenOf("admin@example.com", "일반관리자");
		outsiderToken = tokenOf("outsider@example.com", "남의모임");

		groupId = createGroup(ownerToken);
		joinGroup(adminToken, invitationCode(ownerToken, groupId));
	}

	@Test
	void 소속되지_않은_모임은_조회할_수_없다() {
		Response detail = http.get("/api/v1/groups/" + groupId, outsiderToken);
		Response members = http.get("/api/v1/groups/" + groupId + "/members", outsiderToken);
		Response memberships = http.get("/api/v1/groups/" + groupId + "/memberships", outsiderToken);

		assertThat(detail.status()).isEqualTo(403);
		assertThat(detail.at("code")).isEqualTo("ACCESS_DENIED");
		assertThat(members.status()).isEqualTo(403);
		assertThat(memberships.status()).isEqualTo(403);
	}

	@Test
	void 내_모임_목록에는_소속된_모임만_보인다() {
		Response outsiderGroups = http.get("/api/v1/groups", outsiderToken);
		Response adminGroups = http.get("/api/v1/groups", adminToken);

		assertThat((List<?>) outsiderGroups.at("data")).isEmpty();
		assertThat((List<?>) adminGroups.at("data")).hasSize(1);
	}

	@Test
	void 일반_관리자는_총무_전용_기능을_사용할_수_없다() {
		Response addMember = http.postJson("/api/v1/groups/" + groupId + "/members",
				Map.of("name", "김모임원"), adminToken);
		Response invitation = http.postJson("/api/v1/groups/" + groupId + "/invitations",
				Map.of(), adminToken);
		Response update = http.patchJson("/api/v1/groups/" + groupId,
				Map.of("name", "바뀐이름"), adminToken);
		Response delete = http.delete("/api/v1/groups/" + groupId, adminToken);

		assertThat(addMember.status()).isEqualTo(403);
		assertThat(invitation.status()).isEqualTo(403);
		assertThat(update.status()).isEqualTo(403);
		assertThat(delete.status()).isEqualTo(403);
	}

	@Test
	void 존재하지_않는_모임은_404() {
		Response response = http.get("/api/v1/groups/999999", ownerToken);

		assertThat(response.status()).isEqualTo(404);
		assertThat(response.at("code")).isEqualTo("GROUP_NOT_FOUND");
	}

	@Test
	void 인증_없이는_접근할_수_없다() {
		assertThat(http.get("/api/v1/groups", null).status()).isEqualTo(401);
	}

	@Test
	void 모임_상세의_memberCount는_납부_명단_수다() {
		// 관리자 2명(총무·일반) 이 있어도 명단에 등록하지 않으면 memberCount 는 0이다.
		Response before = http.get("/api/v1/groups/" + groupId, ownerToken);
		assertThat(before.at("data.myRole")).isEqualTo("OWNER");
		assertThat(before.at("data.memberCount")).isEqualTo(0);
		assertThat(before.at("data.ownerCount")).isEqualTo(1);

		http.postJson("/api/v1/groups/" + groupId + "/members", Map.of("name", "김모임원"), ownerToken);

		Response after = http.get("/api/v1/groups/" + groupId, ownerToken);
		assertThat(after.at("data.memberCount")).isEqualTo(1);
	}

	// --- 모임 삭제 확인(모임명 입력) ---

	@Test
	void 입력한_모임명이_맞으면_삭제된다() {
		Response delete = http.deleteJson("/api/v1/groups/" + groupId, Map.of("confirmName", "주리랑"), ownerToken);

		assertThat(delete.status()).isEqualTo(204);
		assertThat(http.get("/api/v1/groups/" + groupId, ownerToken).status()).isEqualTo(404);
	}

	@Test
	void 입력한_모임명이_다르면_삭제하지_않는다() {
		Response delete = http.deleteJson("/api/v1/groups/" + groupId, Map.of("confirmName", "주리"), ownerToken);

		assertThat(delete.status()).isEqualTo(400);
		assertThat(delete.at("code")).isEqualTo("GROUP_NAME_MISMATCH");
		assertThat(http.get("/api/v1/groups/" + groupId, ownerToken).status()).isEqualTo(200);
	}

	@Test
	void 본문_없이_부르던_기존_삭제_요청도_그대로_된다() {
		Response delete = http.delete("/api/v1/groups/" + groupId, ownerToken);

		assertThat(delete.status()).isEqualTo(204);
	}

	@Test
	void 일반_관리자는_모임명을_맞게_넣어도_삭제할_수_없다() {
		Response right = http.deleteJson("/api/v1/groups/" + groupId, Map.of("confirmName", "주리랑"), adminToken);
		Response wrong = http.deleteJson("/api/v1/groups/" + groupId, Map.of("confirmName", "아무거나"), adminToken);

		// 이름이 맞든 틀리든 같은 응답이어야 한다 — 권한 없는 사람에게 이름이 맞는지 알려 주지 않는다.
		assertThat(right.status()).isEqualTo(403);
		assertThat(wrong.status()).isEqualTo(403);
		assertThat(http.get("/api/v1/groups/" + groupId, ownerToken).status()).isEqualTo(200);
	}

	private String tokenOf(String email, String name) {
		userRepository.save(User.create(email, passwordEncoder.encode(PASSWORD), name));
		Response login = http.postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD));
		return (String) login.at("data.tokens.accessToken");
	}

	private long createGroup(String token) {
		Response response = http.postJson("/api/v1/groups",
				Map.of("name", "주리랑", "description", "중앙창작음악동아리"), token);
		return ((Number) response.at("data.groupId")).longValue();
	}

	private String invitationCode(String token, long groupId) {
		Response response = http.postJson("/api/v1/groups/" + groupId + "/invitations", Map.of(), token);
		return (String) response.at("data.invitationCode");
	}

	private void joinGroup(String token, String code) {
		http.postJson("/api/v1/groups/join", Map.of("invitationCode", code), token);
	}
}
