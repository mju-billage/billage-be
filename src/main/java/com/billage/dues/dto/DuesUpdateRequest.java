package com.billage.dues.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * 회비 수정 요청. 전달된 필드만 수정하며 마감 전에만 가능하다.
 *
 * <p>{@code amount} 는 수정할 수 없다 — 값이 오면 조용히 무시하지 않고 {@code DUES_AMOUNT_IMMUTABLE} 로 알린다.
 */
public record DuesUpdateRequest(
		@Size(min = 1, max = 20, message = "회비 제목은 1~20자여야 합니다.")
		String title,

		LocalDate dueDate,

		List<Long> targetMemberIds,

		Long ledgerId,

		Long amount
) {
}
