package com.billage.auth.social;

import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 구글 ID Token 검증. 서명 검증까지 포함된 구글 tokeninfo 엔드포인트를 사용해
 * 클라이언트 라이브러리 없이 서명·만료·발급자를 확인한다.
 *
 * <p>실패하면 응답은 모두 {@code SOCIAL_TOKEN_INVALID} 하나지만, 원인은 WARN 로그로 남긴다(토큰 값은 남기지 않는다).
 */
@Slf4j
@Component
class GoogleTokenVerifier implements SocialTokenVerifier {

	private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token={idToken}";

	private final RestClient restClient = SocialHttpClient.create();
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
		} catch (RestClientResponseException e) {
			// 구글 tokeninfo 는 잘못된 토큰에 400 과 {"error": "invalid_token", ...} 를 준다.
			throw rejected("tokeninfo 가 " + e.getStatusCode().value() + " 으로 응답했다: "
					+ truncate(e.getResponseBodyAsString()));
		} catch (RestClientException e) {
			throw rejected("tokeninfo 호출에 실패했다: " + e.getMessage());
		}

		if (payload == null) {
			throw rejected("tokeninfo 응답이 비었다");
		}
		if (!Objects.equals(properties.clientId(), payload.get("aud"))) {
			throw rejected("다른 클라이언트에서 발급된 토큰이다 (기대 aud=" + properties.clientId()
					+ ", 실제 aud=" + payload.get("aud") + ")");
		}
		if (!"true".equals(String.valueOf(payload.get("email_verified")))) {
			throw rejected("인증되지 않은 구글 계정 이메일이다");
		}

		String sub = (String) payload.get("sub");
		String email = (String) payload.get("email");
		if (sub == null || email == null) {
			throw rejected("토큰에 sub 또는 email 이 없다");
		}
		return new OAuthUserInfo(sub, email);
	}

	private static BusinessException rejected(String reason) {
		log.warn("구글 로그인 거부: {}", reason);
		return new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
	}

	private static String truncate(String body) {
		return body.length() <= 300 ? body : body.substring(0, 300) + "…";
	}
}
