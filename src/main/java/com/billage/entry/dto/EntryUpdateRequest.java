package com.billage.entry.dto;

import java.time.LocalDate;
import java.util.List;

import com.billage.entry.Entry;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 내역 수정 요청. 전달된 필드만 수정하며 null 은 "변경 없음"이다.
 * 유형(type)과 승인 상태는 수정할 수 없다.
 *
 * <p>공백 전용 내역명(" ")은 {@code @Size(min = 1)} 을 통과하므로 Service 에서 막는다.
 * {@code @NotBlank} 는 null 까지 거부해 부분 수정 규칙을 깨뜨리므로 쓰지 않는다.
 */
public record EntryUpdateRequest(
		@Size(min = 1, max = 20, message = "내역명은 1자 이상 20자 이하여야 합니다.")
		String title,

		@Positive(message = "금액은 0보다 커야 합니다.")
		@Max(value = Entry.MAX_AMOUNT, message = "금액은 999,999,999원 이하여야 합니다.")
		Long amount,

		LocalDate occurredOn,

		@Size(max = 30, message = "메모는 30자 이하여야 합니다.")
		String memo,

		/** 담당자 변경. null 이면 그대로 둔다. */
		Long managerUserId,

		/**
		 * 증빙 파일 전체 교체. 전달하면 이 목록이 최종 상태가 되고,
		 * 빠진 증빙은 저장소에서도 삭제된다. null 이면 기존 증빙을 그대로 둔다.
		 */
		List<Long> receiptFileIds
) {
}
