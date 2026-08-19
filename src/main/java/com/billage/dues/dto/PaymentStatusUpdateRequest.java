package com.billage.dues.dto;

import jakarta.validation.constraints.NotBlank;

/** 납부 상태 변경 요청. 허용 값은 UNPAID / PAID 뿐이다. */
public record PaymentStatusUpdateRequest(
		@NotBlank(message = "납부 상태는 필수입니다.")
		String status
) {
}
