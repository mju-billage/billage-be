package com.billage.auth.dto;

import com.billage.user.User;

/**
 * 이메일 회원가입 응답. 토큰은 포함하지 않는다(가입 후 별도 로그인).
 * 식별자 필드명은 노션 명세·프론트 계약에 맞춰 {@code userId} 를 사용한다.
 */
public record SignupResponse(
		Long userId,
		String email,
		String name
) {

	public static SignupResponse from(User user) {
		return new SignupResponse(user.getId(), user.getEmail(), user.getName());
	}
}
