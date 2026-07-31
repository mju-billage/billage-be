package com.billage.auth.dto;

import com.billage.user.User;

/**
 * 로그인 응답: 사용자 정보 + 토큰 쌍.
 */
public record LoginResponse(
		UserResponse user,
		String accessToken,
		String refreshToken,
		String tokenType,
		long accessTokenExpiresIn
) {

	public static LoginResponse of(User user, String accessToken, String refreshToken, long accessTokenExpiresIn) {
		return new LoginResponse(UserResponse.from(user), accessToken, refreshToken, "Bearer", accessTokenExpiresIn);
	}
}
