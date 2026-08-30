package com.billage.dues;

/**
 * 마감된 회비의 납부자 한 명. 내역 상세가 "납부자 명수 / 명단"을 보여 줄 때 쓴다.
 *
 * <p>내역(Entry) 도메인이 회비 내부 구조를 알지 않아도 되도록 최소한만 담는다.
 */
public record DuesPayerView(Long memberId, String name, Long amount) {
}
