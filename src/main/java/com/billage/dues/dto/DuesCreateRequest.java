package com.billage.dues.dto;

import java.time.LocalDate;
import java.util.List;

import com.billage.dues.Dues;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 회비 생성 요청. */
public record DuesCreateRequest(
		@NotBlank(message = "회비 제목은 필수입니다.")
		@Size(max = 20, message = "회비 제목은 20자 이하여야 합니다.")
		String title,

		@NotNull(message = "금액은 필수입니다.")
		@Positive(message = "금액은 0보다 커야 합니다.")
		@Max(value = Dues.MAX_AMOUNT, message = "금액은 999,999,999원 이하여야 합니다.")
		Long amount,

		@NotNull(message = "마감일은 필수입니다.")
		LocalDate dueDate,

		@NotEmpty(message = "납부 대상을 1명 이상 지정해야 합니다.")
		List<Long> targetMemberIds,

		@NotNull(message = "장부는 필수입니다.")
		Long ledgerId
) {
}
