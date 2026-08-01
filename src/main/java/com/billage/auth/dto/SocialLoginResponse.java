package com.billage.auth.dto;

/**
 * 소셜 로그인 결과. 이미 연결된 계정이면 {@code LOGIN}으로 토큰을 바로 발급하고,
 * 최초 로그인이면 {@code SIGNUP_REQUIRED}로 이메일만 알려줘 클라이언트가 약관 동의·이름 입력 화면으로 이동하게 한다.
 */
public record SocialLoginResponse(
		Status status,
		LoginResponse login,
		String email
) {

	public enum Status {
		LOGIN,
		SIGNUP_REQUIRED
	}

	public static SocialLoginResponse loggedIn(LoginResponse login) {
		return new SocialLoginResponse(Status.LOGIN, login, null);
	}

	public static SocialLoginResponse signupRequired(String email) {
		return new SocialLoginResponse(Status.SIGNUP_REQUIRED, null, email);
	}
}
