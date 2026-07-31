package com.billage.auth.dto;

/**
 * 토큰 재발급 응답. 새 Access/Refresh Token을 함께 반환한다.
 */
public record TokenResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long accessTokenExpiresIn
) {

	public static TokenResponse of(String accessToken, String refreshToken, long accessTokenExpiresIn) {
		return new TokenResponse(accessToken, refreshToken, "Bearer", accessTokenExpiresIn);
	}
}
