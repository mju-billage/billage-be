package com.billage.auth.dto;

import com.billage.user.User;

/**
 * 인증 응답에 포함되는 사용자 정보. 비밀번호 등 민감 정보는 제외한다.
 */
public record UserResponse(
		Long id,
		String email,
		String name
) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getName());
	}
}
