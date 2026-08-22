package com.billage.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 모임원 이름 수정. 바꿀 수 있는 값이 이름 하나뿐이라 부분 수정이 아니며 필수값이다.
 */
public record MemberUpdateRequest(
		@NotBlank(message = "모임원 이름은 필수입니다.")
		@Size(max = 10, message = "모임원 이름은 10자 이하여야 합니다.")
		String name
) {
}
