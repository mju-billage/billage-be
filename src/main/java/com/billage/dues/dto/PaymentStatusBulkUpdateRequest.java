package com.billage.dues.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 납부 상태 일괄 변경 요청.
 *
 * <p>화면은 체크박스로 여러 명을 고른 뒤 버튼 한 번으로 상태를 바꾼다. 단건 API 를 N 번 부르면
 * 중간에 실패했을 때 일부만 반영된 채 남고, 스낵바가 알려 줄 "{N}명" 도 클라이언트가 세야 한다.
 */
public record PaymentStatusBulkUpdateRequest(
		@NotEmpty(message = "대상 모임원을 1명 이상 지정해야 합니다.")
		List<Long> memberIds,

		@NotBlank(message = "납부 상태는 필수입니다.")
		String status
) {
}
