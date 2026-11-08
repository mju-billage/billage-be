package com.billage.helpcenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import com.billage.auth.jwt.JwtTokenProvider;
import com.billage.support.HttpTestClient;
import com.billage.support.HttpTestClient.Response;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 고객지원 API — FAQ·공지·약관은 로그인 없이, 문의는 로그인 후. 내용은 마이그레이션으로 넣으므로
 * 테스트도 SQL 로 직접 넣는다(테스트 전 DB 정리가 V25 시드까지 비운다).
 */
class HelpCenterIntegrationTest extends IntegrationTest {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;
	@Autowired
	JwtTokenProvider jwtTokenProvider;
	@Autowired
	UserRepository userRepository;
	@Autowired
	InquiryRepository inquiryRepository;

	private HttpTestClient http;

	@BeforeEach
	void setUp() {
		http = new HttpTestClient(port);
	}

	@Test
	void FAQ_는_로그인_없이_정렬_순서대로_받고_줄바꿈을_그대로_준다() {
		jdbcTemplate.update("insert into faq (question, answer, sort_order) values (?, ?, ?)", "두번째", "답2", 2);
		jdbcTemplate.update("insert into faq (question, answer, sort_order) values (?, ?, ?)", "첫번째",
				"첫 줄\n\n셋째 줄", 1);

		Response response = http.get("/api/v1/faqs", null);

		assertThat(response.status()).isEqualTo(200);
		List<Map<String, Object>> faqs = (List<Map<String, Object>>) response.at("data");
		assertThat(faqs).extracting(faq -> faq.get("question")).containsExactly("첫번째", "두번째");
		assertThat(faqs.get(0).get("answer")).isEqualTo("첫 줄\n\n셋째 줄");
		assertThat(faqs.get(0).get("faqId")).isNotNull();
	}

	@Test
	void 공지는_로그인_없이_최신순_목록과_상세를_받는다() {
		jdbcTemplate.update("insert into notice (title, body, created_at) values (?, ?, ?)",
				"옛 공지", "옛 본문", "2026-10-01 10:00:00");
		jdbcTemplate.update("insert into notice (title, body, created_at) values (?, ?, ?)",
				"새 공지", "새 본문", "2026-11-01 10:00:00");

		Response list = http.get("/api/v1/notices", null);

		assertThat(list.status()).isEqualTo(200);
		List<Map<String, Object>> content = (List<Map<String, Object>>) list.at("data.content");
		assertThat(content).extracting(notice -> notice.get("title")).containsExactly("새 공지", "옛 공지");
		assertThat(content.get(0)).containsKeys("noticeId", "createdAt").doesNotContainKey("body");

		Object noticeId = content.get(0).get("noticeId");
		Response detail = http.get("/api/v1/notices/" + noticeId, null);
		assertThat(detail.status()).isEqualTo(200);
		assertThat(detail.at("data.body")).isEqualTo("새 본문");

		assertThat(http.get("/api/v1/notices/999999", null).at("code")).isEqualTo("NOTICE_NOT_FOUND");
	}

	@Test
	void 약관은_로그인_없이_받고_원문이_없으면_404() {
		jdbcTemplate.update("insert into terms (term_type, body, updated_at) values (?, ?, now())",
				"SERVICE", "제1조 목적");

		Response service = http.get("/api/v1/terms/SERVICE", null);
		assertThat(service.status()).isEqualTo(200);
		assertThat(service.at("data.termType")).isEqualTo("SERVICE");
		assertThat(service.at("data.body")).isEqualTo("제1조 목적");

		Response privacy = http.get("/api/v1/terms/PRIVACY", null);
		assertThat(privacy.status()).isEqualTo(404);
		assertThat(privacy.at("code")).isEqualTo("TERMS_NOT_FOUND");
	}

	@Test
	void 문의는_로그인해야_하고_저장된다() {
		Map<String, String> body = Map.of("email", "member@example.com", "title", "문의 제목",
				"content", "문의 내용");

		assertThat(http.postJson("/api/v1/inquiries", body).status()).isEqualTo(401);

		Long userId = userRepository.save(User.create("member@example.com", "encoded", "회원")).getId();
		Response response = http.postJson("/api/v1/inquiries", body, jwtTokenProvider.createAccessToken(userId));

		assertThat(response.status()).isEqualTo(204);
		assertThat(inquiryRepository.findAll()).singleElement().satisfies(inquiry -> {
			assertThat(inquiry.getUserId()).isEqualTo(userId);
			assertThat(inquiry.getEmail()).isEqualTo("member@example.com");
			assertThat(inquiry.getTitle()).isEqualTo("문의 제목");
			assertThat(inquiry.getCreatedAt()).isNotNull();
		});
	}

	@Test
	void 문의는_이메일_형식과_길이를_검사한다() {
		Long userId = userRepository.save(User.create("member@example.com", "encoded", "회원")).getId();
		String token = jwtTokenProvider.createAccessToken(userId);

		Response badEmail = http.postJson("/api/v1/inquiries",
				Map.of("email", "not-an-email", "title", "제목", "content", "내용"), token);
		Response longTitle = http.postJson("/api/v1/inquiries",
				Map.of("email", "member@example.com", "title", "가".repeat(51), "content", "내용"), token);
		Response blankContent = http.postJson("/api/v1/inquiries",
				Map.of("email", "member@example.com", "title", "제목", "content", "   "), token);

		assertThat(List.of(badEmail.status(), longTitle.status(), blankContent.status())).containsOnly(400);
		assertThat(inquiryRepository.count()).isZero();
	}

	@Test
	void 문의는_한_사람당_하루_5건까지다() {
		Long userId = userRepository.save(User.create("member@example.com", "encoded", "회원")).getId();
		String token = jwtTokenProvider.createAccessToken(userId);
		Map<String, String> body = Map.of("email", "member@example.com", "title", "제목", "content", "내용");

		for (int i = 0; i < Inquiry.DAILY_LIMIT_PER_USER; i++) {
			assertThat(http.postJson("/api/v1/inquiries", body, token).status()).isEqualTo(204);
		}
		Response sixth = http.postJson("/api/v1/inquiries", body, token);

		assertThat(sixth.status()).isEqualTo(429);
		assertThat(sixth.at("code")).isEqualTo("INQUIRY_LIMIT_EXCEEDED");
		assertThat(inquiryRepository.count()).isEqualTo(Inquiry.DAILY_LIMIT_PER_USER);
	}

	@Test
	void 탈퇴하면_문의는_남고_작성자_연결만_끊긴다() {
		Long userId = userRepository.save(User.create("member@example.com", "encoded", "회원")).getId();
		http.postJson("/api/v1/inquiries", Map.of("email", "member@example.com", "title", "제목", "content", "내용"),
				jwtTokenProvider.createAccessToken(userId));

		userRepository.deleteById(userId);
		userRepository.flush();

		assertThat(inquiryRepository.findAll()).singleElement()
				.satisfies(inquiry -> assertThat(inquiry.getUserId()).isNull());
	}
}
