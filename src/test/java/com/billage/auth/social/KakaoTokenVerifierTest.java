package com.billage.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

class KakaoTokenVerifierTest {

	private static final long APP_ID = 1234567L;
	private static final String TOKEN_INFO = "https://kapi.kakao.com/v1/user/access_token_info";
	private static final String USER_ME = "https://kapi.kakao.com/v2/user/me";

	private MockRestServiceServer server;
	private RestClient restClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		restClient = builder.build();
	}

	private KakaoTokenVerifier verifier(Long appId) {
		return new KakaoTokenVerifier(new KakaoAuthProperties(appId), restClient);
	}

	private void tokenInfo(long appId) {
		server.expect(requestTo(TOKEN_INFO))
				.andExpect(header("Authorization", "Bearer kakao-token"))
				.andRespond(withSuccess("{\"id\":42,\"expires_in\":7199,\"app_id\":" + appId + "}",
						MediaType.APPLICATION_JSON));
	}

	private void userMe(String json) {
		server.expect(requestTo(USER_ME)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	@Test
	void 우리_앱_토큰이면_회원번호와_이메일을_돌려준다() {
		tokenInfo(APP_ID);
		userMe("{\"id\":42,\"kakao_account\":{\"email\":\"a@kakao.com\",\"is_email_verified\":true}}");

		OAuthUserInfo info = verifier(APP_ID).verify("kakao-token");

		assertThat(info.providerUserId()).isEqualTo("42");
		assertThat(info.email()).isEqualTo("a@kakao.com");
		server.verify();
	}

	@Test
	void 다른_앱에서_발급된_토큰은_사용자_정보를_부르지_않고_거부한다() {
		tokenInfo(999L);

		assertRejected(verifier(APP_ID));
		server.verify();
	}

	@Test
	void 카카오가_토큰을_거부하면_SOCIAL_TOKEN_INVALID() {
		server.expect(requestTo(TOKEN_INFO)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"msg\":\"this access token does not exist\",\"code\":-401}"));

		assertRejected(verifier(APP_ID));
	}

	@Test
	void 이메일_동의가_없으면_거부한다() {
		tokenInfo(APP_ID);
		userMe("{\"id\":42,\"kakao_account\":{\"email_needs_agreement\":true}}");

		assertRejected(verifier(APP_ID));
	}

	@Test
	void 인증되지_않은_이메일이면_거부한다() {
		tokenInfo(APP_ID);
		userMe("{\"id\":42,\"kakao_account\":{\"email\":\"a@kakao.com\",\"is_email_verified\":false}}");

		assertRejected(verifier(APP_ID));
	}

	@Test
	void 앱_ID가_설정되지_않았으면_카카오를_부르지_않고_거부한다() {
		assertRejected(verifier(null));
		server.verify();
	}

	private void assertRejected(KakaoTokenVerifier verifier) {
		assertThatThrownBy(() -> verifier.verify("kakao-token"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.SOCIAL_TOKEN_INVALID);
	}
}
