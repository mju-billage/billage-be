package com.billage.auth.social;

/**
 * Provider가 발급한 토큰을 검증하고 사용자 식별 정보를 얻어온다.
 * 구현체는 {@link #provider()}로 자신을 식별하며, {@link SocialAuthService}가 provider별로 선택해 위임한다.
 */
public interface SocialTokenVerifier {

	SocialProvider provider();

	/**
	 * @param token 구글은 ID Token, 카카오는 Access Token
	 * @throws com.billage.common.exception.BusinessException 토큰이 유효하지 않으면 {@code SOCIAL_TOKEN_INVALID}
	 */
	OAuthUserInfo verify(String token);
}
