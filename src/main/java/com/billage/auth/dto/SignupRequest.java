package com.billage.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이메일 회원가입 요청. 가입만 처리하고 토큰은 발급하지 않으므로, 클라이언트는 이어서 로그인을 호출한다.
 *
 * @param password 프론트({@code utils/validators.ts})와 같은 규칙을 서버에서도 검증한다.
 *                 상한 72자는 정책이 아니라 BCrypt 제약이다 — 그 뒤 바이트는 해시에 반영되지 않아
 *                 서로 다른 긴 비밀번호가 같은 것으로 취급될 수 있어 미리 막는다.
 */
public record SignupRequest(
		@NotBlank @Email String email,

		@NotBlank
		@Size(max = 72, message = "비밀번호는 72자 이하여야 합니다.")
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
				message = "비밀번호는 영문 대문자·소문자·숫자·특수문자를 포함해 8자 이상이어야 합니다.")
		String password,

		@NotBlank @Size(max = 10, message = "이름은 10자 이하여야 합니다.") String name
) {
}
