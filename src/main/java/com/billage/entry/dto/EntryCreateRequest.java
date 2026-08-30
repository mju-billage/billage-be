package com.billage.entry.dto;

import java.time.LocalDate;
import java.util.List;

import com.billage.entry.Entry;
import com.billage.entry.EntryType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 내역 등록 요청. */
public record EntryCreateRequest(
		@NotNull(message = "내역 유형은 필수입니다.")
		EntryType type,

		@NotBlank(message = "내역명은 필수입니다.")
		@Size(max = 20, message = "내역명은 20자 이하여야 합니다.")
		String title,

		@NotNull(message = "금액은 필수입니다.")
		@Positive(message = "금액은 0보다 커야 합니다.")
		@Max(value = Entry.MAX_AMOUNT, message = "금액은 999,999,999원 이하여야 합니다.")
		Long amount,

		@NotNull(message = "발생일은 필수입니다.")
		LocalDate occurredOn,

		@Size(max = 30, message = "메모는 30자 이하여야 합니다.")
		String memo,

		/**
		 * 담당자(모임 관리자). 보내지 않으면 등록자 본인이 된다 — 화면의 담당자 기본값 규칙과 같다.
		 * 납부 명단(Member)이 아니라 가입 사용자여야 한다.
		 */
		Long managerUserId,

		/** 미리 업로드한 증빙 파일 ID. 본인이 올린 RECEIPT 파일 중 아직 연결되지 않은 것만 허용된다. */
		List<Long> receiptFileIds
) {
}
