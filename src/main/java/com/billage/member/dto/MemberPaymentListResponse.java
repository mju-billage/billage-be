package com.billage.member.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 「모임원 상세 > 납부 내역」 응답.
 *
 * <p>상단에 총 납부 금액을 고정 노출하므로 목록과 함께 담는다. 리스트는 조회 전용이라
 * 항목을 눌러도 이동하지 않는다(화면명세 "리스트 개별 항목 터치 시 액션없음. 단순 조회용").
 */
public record MemberPaymentListResponse(
		long totalPaidAmount,
		List<Payment> payments
) {

	public record Payment(
			Long duesId,
			String duesTitle,
			Long ledgerId,
			String ledgerName,
			Long amount,
			OffsetDateTime paidAt
	) {
	}
}
