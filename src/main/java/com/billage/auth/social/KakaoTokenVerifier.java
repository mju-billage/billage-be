package com.billage.auth.social;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 Access Token 검증. 카카오 사용자 정보 API 호출 자체가 토큰 유효성 검증을 겸한다
 * (유효하지 않은 토큰이면 401을 응답).
 */
@Component
class KakaoTokenVerifier implements SocialTokenVerifier {

	private static final String USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

	private final RestClient restClient = SocialHttpClient.create();

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
		KakaoUserResponse response;
		try {
			response = restClient.get()
					.uri(USER_ME_URL)
					.header("Authorization", "Bearer " + accessToken)
					.retrieve()
					.body(KakaoUserResponse.class);
		} catch (RestClientException e) {
			throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
		}

		if (response == null || response.id() == null || response.kakaoAccount() == null
				|| !response.kakaoAccount().emailVerified() || response.kakaoAccount().email() == null) {
			throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
		}
		return new OAuthUserInfo(String.valueOf(response.id()), response.kakaoAccount().email());
	}
}
