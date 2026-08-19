package com.billage.ledger.dto;

import jakarta.validation.constraints.Size;

/** 부분 수정. 예산은 별도 API(`PATCH /ledgers/{id}/budget`)로 변경한다. */
public record LedgerUpdateRequest(
		// 공백 전용 이름은 Service 에서 막는다(@NotBlank 는 null 까지 거부해 부분 수정 규칙을 깬다).
		@Size(min = 1, max = 20, message = "장부 이름은 1~20자여야 합니다.")
		String name,

		/** 이동할 폴더. 같은 모임의 폴더만 지정할 수 있다. */
		Long folderId
) {
}
