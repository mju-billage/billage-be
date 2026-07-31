package com.billage.auth.social;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

/**
 * 구글 ID Token 검증. 서명 검증까지 포함된 구글 tokeninfo 엔드포인트를 사용해
 * 클라이언트 라이브러리 없이 서명·만료·발급자를 확인한다.
 */
@Component
class GoogleTokenVerifier implements SocialTokenVerifier {

	private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token={idToken}";

	private final RestClient restClient = RestClient.create();
	private final SocialAuthProperties properties;

	GoogleTokenVerifier(SocialAuthProperties properties) {
		this.properties = properties;
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.GOOGLE;
	}

	@Override
	@SuppressWarnings("unchecked")
	public OAuthUserInfo verify(String idToken) {
		Map<String, Object> payload;
		try {
			payload = restClient.get()
					.uri(TOKENINFO_URL, idToken)
					.retrieve()
					.body(Map.class);
		} catch (RestClientException e) {
			throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
		}

		if (payload == null || !properties.clientId().equals(payload.get("aud"))
				|| !"true".equals(String.valueOf(payload.get("email_verified")))) {
			throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
		}

		String sub = (String) payload.get("sub");
		String email = (String) payload.get("email");
		if (sub == null || email == null) {
			throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
		}
		return new OAuthUserInfo(sub, email);
	}
}
