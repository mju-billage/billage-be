package com.billage.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 모임 생성 요청. 모임명은 글로벌 정책상 최대 10자.
 */
public record CreateGroupRequest(
		@NotBlank @Size(max = 10) String name
) {
}
