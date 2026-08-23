package com.billage.member.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 모임원 개별 추가. 이름만 필수이고 전화번호·태그·메모는 선택값이다.
 * 전화번호는 하이픈을 넣어 보내도 되며 서버가 숫자만 남겨 저장한다.
 */
public record MemberCreateRequest(
		@NotBlank(message = "모임원 이름은 필수입니다.")
		@Size(max = 10, message = "모임원 이름은 10자 이하여야 합니다.")
		String name,

		@Size(max = 20, message = "전화번호가 너무 깁니다.")
		String phoneNumber,

		@Size(max = 10, message = "태그는 10개 이하여야 합니다.")
		List<@Size(max = 10, message = "태그는 10자 이하여야 합니다.") String> tags,

		@Size(max = 30, message = "메모는 30자 이하여야 합니다.")
		String memo
) {
}
