package com.billage.dues;

import java.time.LocalDateTime;

/**
 * 한 모임원이 낸 회비 한 건. 「모임원 상세 &gt; 납부 내역」 화면이 쓴다.
 *
 * <p>모임원(Member) 도메인이 회비 내부 구조를 알지 않아도 되도록 표시에 필요한 값만 담는다.
 */
public record MemberPaymentView(
		Long duesId,
		String duesTitle,
		Long ledgerId,
		String ledgerName,
		Long amount,
		LocalDateTime paidAt
) {
}
