package com.billage.group;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.billage.support.HttpTestClient;
import com.billage.support.HttpTestClient.ListResponse;
import com.billage.support.HttpTestClient.Response;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 초대 코드 참여·재발급과 관리자 목록 검증.
 * <p>
 * 참여자가 GENERAL 권한만 받고 모임원 명단에는 등록되지 않는다는 점,
 * 재발급 후 이전 코드가 무효가 된다는 점이 핵심이다.
 */
class GroupJoinIntegrationTest extends IntegrationTest {

	private static final String PASSWORD = "password123!";

	@LocalServerPort
	int port;

	@Autowired
	UserRepository userRepository;
	@Autowired
	GroupManagerRepository groupManagerRepository;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	JdbcTemplate jdbcTemplate;

	private HttpTestClient http;
	private String ownerToken;
	private String joinerToken;
	private User joiner;
	private Long groupId;
	private String inviteCode;

	@BeforeEach
	void setUp() {
		http = new HttpTestClient(port);
		createUser("owner@example.com", "총무");
		joiner = createUser("joiner@example.com", "참여자");
		ownerToken = login("owner@example.com");
		joinerToken = login("joiner@example.com");

		Response group = http.postJson("/api/v1/groups", Map.of("name", "테스트모임"), ownerToken);
		groupId = ((Number) group.at("id")).longValue();
		inviteCode = (String) group.at("inviteCode");
	}

	// --- 참여 ---

	@Test
	void 초대코드로_참여하면_GENERAL_관리자가_된다() {
		Response response = join(joinerToken, inviteCode);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("id")).isEqualTo(groupId.intValue());
		assertThat(response.at("myRole")).isEqualTo("GENERAL");

		GroupManager manager = groupManagerRepository
				.findByGroupIdAndUserId(groupId, joiner.getId()).orElseThrow();
		assertThat(manager.getRole()).isEqualTo(ManagerRole.GENERAL);
	}

	@Test
	void 초대코드는_소문자나_공백이_섞여도_정규화되어_참여된다() {
		Response response = join(joinerToken, "  " + inviteCode.toLowerCase() + "  ");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("myRole")).isEqualTo("GENERAL");
	}

	@Test
	void 참여하면_내_모임_목록에_GENERAL로_나타난다() {
		join(joinerToken, inviteCode);

		ListResponse response = http.getList("/api/v1/groups", joinerToken);

		assertThat(response.body()).hasSize(1);
		assertThat(response.body().getFirst()).containsEntry("name", "테스트모임");
		assertThat(response.body().getFirst()).containsEntry("myRole", "GENERAL");
	}

	@Test
	void 없는_초대코드로_참여하면_404() {
		Response response = join(joinerToken, "ZZZZZZZZ");

		assertThat(response.status()).isEqualTo(404);
		assertThat(response.at("code")).isEqualTo("INVITE_CODE_INVALID");
	}

	@Test
	void 이미_참여한_모임에_다시_참여하면_409() {
		join(joinerToken, inviteCode);

		Response response = join(joinerToken, inviteCode);

		assertThat(response.status()).isEqualTo(409);
		assertThat(response.at("code")).isEqualTo("ALREADY_GROUP_MANAGER");
	}

	@Test
	void 총무가_자기_모임에_참여를_시도하면_409() {
		Response response = join(ownerToken, inviteCode);

		assertThat(response.status()).isEqualTo(409);
		assertThat(response.at("code")).isEqualTo("ALREADY_GROUP_MANAGER");
	}

	@Test
	void 보관된_모임에는_참여할_수_없다() {
		// 보관 API는 아직 없으므로 상태만 직접 바꿔 가드를 검증한다
		jdbcTemplate.update("update `groups` set status = 'ARCHIVED' where id = ?", groupId);

		Response response = join(joinerToken, inviteCode);

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.at("code")).isEqualTo("GROUP_NOT_ACTIVE");
	}

	// --- 초대 코드 재발급 ---

	@Test
	void 총무는_초대코드를_재발급할_수_있고_이전_코드는_무효가_된다() {
		Response response = http.postJson("/api/v1/groups/" + groupId + "/invite-code", Map.of(), ownerToken);

		assertThat(response.status()).isEqualTo(200);
		String newCode = (String) response.at("inviteCode");
		assertThat(newCode).hasSize(8).isNotEqualTo(inviteCode);

		assertThat(join(joinerToken, inviteCode).status()).isEqualTo(404); // 이전 코드
		assertThat(join(joinerToken, newCode).status()).isEqualTo(200);    // 새 코드
	}

	@Test
	void 일반관리자는_초대코드를_재발급할_수_없다() {
		join(joinerToken, inviteCode);

		Response response = http.postJson("/api/v1/groups/" + groupId + "/invite-code", Map.of(), joinerToken);

		assertThat(response.status()).isEqualTo(403);
		assertThat(response.at("code")).isEqualTo("NOT_GROUP_OWNER");
	}

	@Test
	void 초대코드_재발급과_참여가_동시에_일어나면_이전_코드로_참여할_수_없다() throws Exception {
		// 참여와 재발급을 동시에 수행해 경쟁 조건을 검증한다
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(2);
		AtomicReference<Response> joinResponse = new AtomicReference<>();
		AtomicReference<Response> regenerateResponse = new AtomicReference<>();

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			// 스레드 1: 초대 코드로 참여
			executor.submit(() -> {
				try {
					startLatch.await();
					joinResponse.set(join(joinerToken, inviteCode));
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					doneLatch.countDown();
				}
			});

			// 스레드 2: 초대 코드 재발급
			executor.submit(() -> {
				try {
					startLatch.await();
					regenerateResponse.set(http.postJson("/api/v1/groups/" + groupId + "/invite-code", Map.of(), ownerToken));
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					doneLatch.countDown();
				}
			});

			startLatch.countDown();
			doneLatch.await();

			// 재발급은 성공해야 한다
			assertThat(regenerateResponse.get().status()).isEqualTo(200);
			String newCode = (String) regenerateResponse.get().at("inviteCode");
			assertThat(newCode).isNotEqualTo(inviteCode);

			// 참여 결과: 성공(새 코드로 참여됨) 또는 실패(이전 코드 무효)
			// 경쟁에 따라 둘 중 하나. 중요한 것은 이전 코드로는 더 이상 참여할 수 없다는 점이다
			if (joinResponse.get().status() == 200) {
				// 락을 먼저 획득해 참여가 성공한 경우, 재발급이 나중에 적용됨
				assertThat(groupManagerRepository.existsByGroupIdAndUserId(groupId, joiner.getId())).isTrue();
			} else {
				// 재발급이 먼저 적용돼 이전 코드가 무효가 된 경우
				assertThat(joinResponse.get().status()).isEqualTo(404);
				assertThat(joinResponse.get().at("code")).isEqualTo("INVITE_CODE_INVALID");
			}

			// 어떤 경우든 이전 코드로는 더 이상 참여할 수 없다
			Response oldCodeJoin = join(joinerToken, inviteCode);
			assertThat(oldCodeJoin.status()).isIn(404, 409); // 무효 코드 또는 이미 참여됨
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 일반관리자도_초대코드를_조회해_공유할_수_있다() {
		join(joinerToken, inviteCode);

		Response response = http.get("/api/v1/groups/" + groupId + "/invite-code", joinerToken);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("inviteCode")).isEqualTo(inviteCode);
	}

	@Test
	void 관리자가_아니면_초대코드를_조회할_수_없다() {
		Response response = http.get("/api/v1/groups/" + groupId + "/invite-code", joinerToken);

		assertThat(response.status()).isEqualTo(403);
		assertThat(response.at("code")).isEqualTo("NOT_GROUP_MANAGER");
	}

	// --- 관리자 목록 ---

	@Test
	void 관리자_목록은_참여한_순서로_OWNER부터_반환한다() {
		join(joinerToken, inviteCode);

		ListResponse response = http.getList("/api/v1/groups/" + groupId + "/managers", ownerToken);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.body()).hasSize(2);
		assertThat(response.body().getFirst()).containsEntry("name", "총무").containsEntry("role", "OWNER");
		assertThat(response.body().get(1)).containsEntry("name", "참여자").containsEntry("role", "GENERAL");
	}

	@Test
	void 관리자가_아니면_관리자_목록을_조회할_수_없다() {
		Response response = http.get("/api/v1/groups/" + groupId + "/managers", joinerToken);

		assertThat(response.status()).isEqualTo(403);
		assertThat(response.at("code")).isEqualTo("NOT_GROUP_MANAGER");
	}

	// --- helpers ---

	private User createUser(String email, String name) {
		return userRepository.save(User.create(email, passwordEncoder.encode(PASSWORD), name));
	}

	private String login(String email) {
		return (String) http.postJson("/api/v1/auth/login",
				Map.of("email", email, "password", PASSWORD)).at("accessToken");
	}

	private Response join(String token, String code) {
		return http.postJson("/api/v1/groups/join", Map.of("inviteCode", code), token);
	}
}
