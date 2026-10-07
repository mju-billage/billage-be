package com.billage.member.dto;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

/**
 * 「모임원 상세 > 납부 내역」 응답.
 *
 * <p>상단에 총 납부 금액을 고정 노출하므로 목록과 함께 담는다. 리스트는 조회 전용이라
 * 항목을 눌러도 이동하지 않는다(화면명세 "리스트 개별 항목 터치 시 액션없음. 단순 조회용").
 *
 * @param totalPaidAmount 이 모임원이 지금까지 낸 전체 금액. 기간·검색어·페이지와 무관하다.
 * @param payments        조건에 맞는 납부 내역. {@code page}·{@code size} 를 주지 않으면 전부 담긴다.
 * @param pageInfo        {@code payments} 가 전체 중 어디인지. 배열을 그대로 둔 채 옆에 덧붙였다 —
 *                        배열을 페이지 객체로 바꾸면 이미 배포된 앱의 파싱이 깨진다.
 */
public record MemberPaymentListResponse(
		long totalPaidAmount,
		List<Payment> payments,
		PageInfo pageInfo
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

	public record PageInfo(
			int page,
			int size,
			long totalElements,
			int totalPages,
			boolean first,
			boolean last
	) {
		public static PageInfo of(Page<?> page) {
			return new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(),
					page.getTotalPages(), page.isFirst(), page.isLast());
		}
	}
}
