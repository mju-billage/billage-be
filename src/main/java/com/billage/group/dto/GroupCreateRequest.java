package com.billage.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupCreateRequest(
		@NotBlank(message = "모임 이름은 필수입니다.")
		@Size(max = 10, message = "모임 이름은 10자 이하여야 합니다.")
		String name,

		@Size(max = 30, message = "모임 설명은 30자 이하여야 합니다.")
		String description
) {
}
