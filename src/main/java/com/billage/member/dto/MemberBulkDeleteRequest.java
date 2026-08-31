package com.billage.member.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * 모임원 일괄 삭제 요청. 「모임원 관리 > 메뉴 > 모임원 삭제」의 다중 선택에 대응한다.
 *
 * <p>단건 삭제를 N 번 부르면 중간에 실패했을 때 일부만 지워진 채 남는다.
 */
public record MemberBulkDeleteRequest(
		@NotEmpty(message = "삭제할 모임원을 1명 이상 선택해야 합니다.")
		List<Long> memberIds
) {
}
