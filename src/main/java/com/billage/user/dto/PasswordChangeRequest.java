package com.billage.user.dto;

import com.billage.common.validation.MaxByteLength;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 비밀번호 변경(화면 ETC-4-PAGE-17-0). 새 비밀번호 확인란은 클라이언트에서만 대조하므로 서버는 받지 않는다.
 *
 * @param refreshToken 선택. 지금 쓰고 있는 기기의 Refresh Token 이며, 주면 그 기기만 로그인 상태로 남고
 *                     나머지 기기는 끊긴다. 주지 않으면 모든 기기가 끊겨 재로그인이 필요하다 —
 *                     서버는 Access Token 만으로는 어느 기기인지 알 수 없어 클라이언트가 알려 줘야 한다.
 */
public record PasswordChangeRequest(
		@NotBlank String currentPassword,

		@NotBlank
		@MaxByteLength(value = 72, message = "비밀번호는 72바이트 이하여야 합니다.")
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
				message = "비밀번호는 영문 대문자·소문자·숫자·특수문자를 포함해 8자 이상이어야 합니다.")
		String newPassword,

		String refreshToken
) {
}
