package com.billage.member.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 모임원 상세 수정. 수정 화면이 항목 전체를 담아 보내는 구조라 부분 수정이 아니라 **통째로 교체**한다.
 * 즉 전화번호·태그·메모를 빼고 보내면 해당 값은 비워진다.
 */
public record MemberUpdateRequest(
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
