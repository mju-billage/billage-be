package com.billage.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 모임원 일괄 추가. 이름만 받는다.
 * 입력 텍스트를 쉼표·띄어쓰기·줄바꿈 기준으로 잘라 각각을 독립된 모임원으로 저장한다.
 */
public record MemberBulkCreateRequest(
		@NotBlank(message = "추가할 모임원 이름을 입력해 주세요.")
		@Size(max = 2000, message = "한 번에 입력할 수 있는 길이를 초과했습니다.")
		String names
) {
}
