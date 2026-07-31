package com.billage.auth.dto;

import com.billage.auth.social.SocialProvider;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 소셜 최초 로그인 시 약관 동의와 이름 입력을 거쳐 가입을 완료한다.
 * 이메일은 Provider 토큰에서 얻으므로 입력받지 않는다.
 *
 * @param name 이미 같은 이메일로 가입된 계정이 있으면 무시하고 기존 이름을 유지한다.
 */
public record SocialSignupRequest(
		@NotNull SocialProvider provider,
		@NotBlank String token,
		@NotBlank @Size(max = 100) String name,
		@AssertTrue(message = "약관 동의가 필요합니다.") boolean termsAgreed
) {
}
