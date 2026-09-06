package com.billage.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 모임 단위 장부 생성. 폴더를 거치지 않고 만들 수 있어야 폴더가 하나도 없는 새 모임에서도 장부를 만든다
 * (폴더는 {@code POST /groups/{groupId}/folders} 로 최상위에 바로 만들 수 있는데 장부만 못 만들던 비대칭을 없앤다).
 *
 * @param folderId 넣을 폴더. 주지 않으면 최상위 영역에 만든다.
 * @param budget   선택. 값 검증은 INVALID_BUDGET 으로 응답하기 위해 Service 에서 한다.
 */
public record GroupLedgerCreateRequest(
		@NotBlank(message = "장부 이름은 필수입니다.")
		@Size(max = 20, message = "장부 이름은 20자 이하여야 합니다.")
		String name,

		Long budget,

		Long folderId
) {
}
