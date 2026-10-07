package com.billage.auth.dto;

import com.billage.auth.social.SocialProvider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 소셜 최초 로그인 시 약관 동의와 이름 입력을 거쳐 가입을 완료한다.
 * 이메일은 Provider 토큰에서 얻으므로 입력받지 않는다.
 *
 * @param name        이미 같은 이메일로 가입된 계정이 있으면 무시하고 기존 이름을 유지한다.
 * @param agreements  약관 항목별 동의. 일반 가입과 같은 모양이다. 오면 이 값으로 판정하고 마케팅 동의를 저장한다.
 * @param termsAgreed 항목을 나누기 전의 필드. {@code agreements} 를 보내지 않는 기존 앱을 위해 남겨 둔다 —
 *                    프론트가 {@code agreements} 로 옮기면 지운다.
 */
public record SocialSignupRequest(
		@NotNull SocialProvider provider,
		@NotBlank String token,
		@NotBlank @Size(max = 10, message = "이름은 10자 이하여야 합니다.") String name,
		@Valid AgreementsRequest agreements,
		Boolean termsAgreed
) {
}
