package com.billage.auth.dto;

import com.billage.auth.social.SocialProvider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param token 구글은 ID Token, 카카오는 Access Token
 */
public record SocialLoginRequest(
		@NotNull SocialProvider provider,
		@NotBlank String token
) {
}
