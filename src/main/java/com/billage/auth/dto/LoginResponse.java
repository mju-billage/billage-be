package com.billage.auth.dto;

import com.billage.user.User;

/**
 * 로그인 응답: 사용자 정보 + 토큰 쌍.
 * 토큰은 노션 명세·프론트 계약대로 {@code tokens} 안에 중첩한다(재발급 응답과 같은 모양).
 */
public record LoginResponse(
		UserResponse user,
		TokenResponse tokens
) {

	public static LoginResponse of(User user, String accessToken, String refreshToken, long accessTokenExpiresIn) {
		return new LoginResponse(UserResponse.from(user),
				TokenResponse.of(accessToken, refreshToken, accessTokenExpiresIn));
	}
}
