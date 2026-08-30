package com.billage.report.dto;

import java.time.LocalDate;
import java.util.List;

import com.billage.entry.EntryType;
import com.billage.report.ReportType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 보고서 생성 요청. 유형에 따라 필요한 항목이 다르므로 유형별 검증은 Service 에서 한다.
 *
 * <p><b>장부별</b>: {@code ledgerIds} 필수, {@code entryType} 선택(기본 전체). 기간은 받지 않으며
 * 담긴 내역의 실제 최소·최대 발생일로 정해진다.
 *
 * <p><b>기간별</b>: {@code startDate}·{@code endDate} 필수. 장부는 받지 않으며 그 기간에 승인된 내역이
 * 있는 장부를 모두 담는다.
 */
public record ReportCreateRequest(
		@NotNull(message = "보고서 유형은 필수입니다.")
		ReportType reportType,

		@NotBlank(message = "보고서 제목은 필수입니다.")
		@Size(max = 20, message = "보고서 제목은 20자 이하여야 합니다.")
		String title,

		/** 장부별 전용, 1개 이상. */
		List<Long> ledgerIds,

		/** 장부별 전용. null 이면 전체(수입+지출). */
		EntryType entryType,

		/** 기간별 전용. */
		LocalDate startDate,

		/** 기간별 전용. */
		LocalDate endDate
) {

	public boolean isByLedger() {
		return reportType == ReportType.BY_LEDGER;
	}
}
