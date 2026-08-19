package com.billage.report.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 보고서 생성 요청. 기간 전후 관계와 장부 소유권은 Service 에서 검증한다. */
public record ReportCreateRequest(
		@NotBlank(message = "보고서 제목은 필수입니다.")
		@Size(max = 20, message = "보고서 제목은 20자 이하여야 합니다.")
		String title,

		@NotEmpty(message = "장부를 1개 이상 선택해야 합니다.")
		List<Long> ledgerIds,

		@NotNull(message = "시작일은 필수입니다.")
		LocalDate startDate,

		@NotNull(message = "종료일은 필수입니다.")
		LocalDate endDate
) {
}
