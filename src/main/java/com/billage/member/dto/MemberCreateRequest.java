package com.billage.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberCreateRequest(
		@NotBlank(message = "모임원 이름은 필수입니다.")
		@Size(max = 10, message = "모임원 이름은 10자 이하여야 합니다.")
		String name
) {
}
