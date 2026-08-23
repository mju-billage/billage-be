package com.billage.auth.dto;

import com.billage.common.validation.MaxByteLength;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이메일 회원가입 요청. 가입만 처리하고 토큰은 발급하지 않으므로, 클라이언트는 이어서 로그인을 호출한다.
 *
 * @param email    상한 254자는 RFC 5321 의 주소 최대 길이이자 {@code users.email VARCHAR(255)} 안에 들어가는 값이다.
 *                 형식만 검사하면 255자를 넘는 주소가 통과해 저장 단계에서 터진다.
 * @param password 프론트({@code utils/validators.ts})와 같은 규칙을 서버에서도 검증한다.
 *                 상한 72<b>바이트</b>는 정책이 아니라 BCrypt 제약이다 — 초과하면 인코더가 예외를 던진다.
 *                 글자 수가 아니라 바이트로 재는 이유는 한글이 글자당 3바이트이기 때문이다
 *                 (27자짜리 한글 비밀번호가 73바이트가 될 수 있다).
 */
public record SignupRequest(
		@NotBlank
		@Email
		@Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
		String email,

		@NotBlank
		@MaxByteLength(value = 72, message = "비밀번호는 72바이트 이하여야 합니다.")
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
				message = "비밀번호는 영문 대문자·소문자·숫자·특수문자를 포함해 8자 이상이어야 합니다.")
		String password,

		@NotBlank @Size(max = 10, message = "이름은 10자 이하여야 합니다.") String name
) {
}
