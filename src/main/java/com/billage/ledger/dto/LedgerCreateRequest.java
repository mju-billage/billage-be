package com.billage.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LedgerCreateRequest(
		@NotBlank(message = "장부 이름은 필수입니다.")
		@Size(max = 20, message = "장부 이름은 20자 이하여야 합니다.")
		String name,

		/** 선택. 값 검증은 INVALID_BUDGET 으로 응답하기 위해 Service 에서 한다. */
		Long budget
) {
}
