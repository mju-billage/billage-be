package com.billage.group.dto;

import jakarta.validation.constraints.Size;

/**
 * 부분 수정. 전달되지 않은(null) 필드는 변경하지 않는다.
 *
 * <p>공백 전용 이름(" ")은 {@code @Size(min = 1)} 을 통과하므로 Service 에서 막는다.
 * {@code @NotBlank} 는 null 까지 거부해 부분 수정 규칙을 깨뜨리므로 쓰지 않는다.
 */
public record GroupUpdateRequest(
		@Size(min = 1, max = 10, message = "모임 이름은 1~10자여야 합니다.")
		String name,

		@Size(max = 30, message = "모임 설명은 30자 이하여야 합니다.")
		String description
) {
}
