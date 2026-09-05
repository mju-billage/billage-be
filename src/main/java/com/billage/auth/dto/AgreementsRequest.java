package com.billage.auth.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 가입 약관 동의(화면 COM-2-PAGE-01-0). 필수 3종은 모두 {@code true} 여야 가입이 된다.
 *
 * @param marketing 선택 항목. 값을 그대로 저장해 이후 수신 동의 이력을 증명한다.
 */
public record AgreementsRequest(
		@NotNull Boolean termsOfService,
		@NotNull Boolean privacyPolicy,
		@NotNull Boolean ageOver14,
		Boolean marketing
) {

	public boolean allRequiredAgreed() {
		return Boolean.TRUE.equals(termsOfService)
				&& Boolean.TRUE.equals(privacyPolicy)
				&& Boolean.TRUE.equals(ageOver14);
	}

	public boolean marketingAgreed() {
		return Boolean.TRUE.equals(marketing);
	}
}
