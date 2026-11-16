package com.billage.auth.social;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 Access Token 검증.
 *
 * <p>두 번 부른다. 먼저 토큰 정보 API 로 이 토큰이 <b>우리 앱</b>에서 발급됐는지(app_id) 확인하고,
 * 그다음 사용자 정보 API 로 회원번호와 이메일을 받는다. 사용자 정보 API 만 부르면 다른 카카오 앱에서 받은
 * 토큰도 통과한다.
 *
 * <p>실패하면 응답은 모두 {@code SOCIAL_TOKEN_INVALID} 하나지만, 원인은 WARN 로그로 남긴다.
 * 토큰 값은 로그에 남기지 않는다.
 */
@Slf4j
@Component
class KakaoTokenVerifier implements SocialTokenVerifier {

	private static final String TOKEN_INFO_URL = "https://kapi.kakao.com/v1/user/access_token_info";
	private static final String USER_ME_URL = "https://kapi.kakao.com/v2/user/me";
	private static final int MAX_LOGGED_BODY = 300;

	private final RestClient restClient;
	private final KakaoAuthProperties properties;

	@Autowired
	KakaoTokenVerifier(KakaoAuthProperties properties) {
		this(properties, SocialHttpClient.create());
	}

	KakaoTokenVerifier(KakaoAuthProperties properties, RestClient restClient) {
		this.properties = properties;
		this.restClient = restClient;
	}

	private record TokenInfo(Long id, @JsonProperty("app_id") Long appId) {
	}

	private record KakaoAccount(String email, @JsonProperty("is_email_verified") boolean emailVerified) {
	}

	private record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.KAKAO;
	}

	@Override
	public OAuthUserInfo verify(String accessToken) {
		if (properties.appId() == null) {
			throw rejected("서버에 KAKAO_APP_ID 가 설정되지 않았다");
		}

		TokenInfo tokenInfo = get(TOKEN_INFO_URL, accessToken, TokenInfo.class, "토큰 정보");
		if (tokenInfo == null || !properties.appId().equals(tokenInfo.appId())) {
			throw rejected("다른 앱에서 발급된 토큰이다 (기대 app_id=" + properties.appId()
					+ ", 실제 app_id=" + (tokenInfo == null ? null : tokenInfo.appId()) + ")");
		}

		KakaoUserResponse response = get(USER_ME_URL, accessToken, KakaoUserResponse.class, "사용자 정보");
		if (response == null || response.id() == null) {
			throw rejected("사용자 정보 응답에 회원번호가 없다");
		}
		KakaoAccount account = response.kakaoAccount();
		if (account == null || account.email() == null) {
			throw rejected("이메일을 받지 못했다 — 동의 항목에서 이메일이 빠졌거나 사용자가 동의하지 않았다");
		}
		if (!account.emailVerified()) {
			throw rejected("인증되지 않은 카카오 계정 이메일이다");
		}
		return new OAuthUserInfo(String.valueOf(response.id()), account.email());
	}

	private <T> T get(String url, String accessToken, Class<T> type, String step) {
		try {
			return restClient.get()
					.uri(url)
					.header("Authorization", "Bearer " + accessToken)
					.retrieve()
					.body(type);
		} catch (RestClientResponseException e) {
			// 카카오 오류 본문은 {"msg": ..., "code": -401} 형태라 토큰이 들어 있지 않다.
			throw rejected(step + " API 가 " + e.getStatusCode().value() + " 으로 응답했다: "
					+ truncate(e.getResponseBodyAsString()));
		} catch (RestClientException e) {
			throw rejected(step + " API 호출에 실패했다: " + e.getMessage());
		}
	}

	private static BusinessException rejected(String reason) {
		log.warn("카카오 로그인 거부: {}", reason);
		return new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
	}

	private static String truncate(String body) {
		return body.length() <= MAX_LOGGED_BODY ? body : body.substring(0, MAX_LOGGED_BODY) + "…";
	}
}
