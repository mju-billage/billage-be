package com.billage.entry.dto;

import com.billage.common.response.PageResponse;

/**
 * 모임 전체 내역 화면(GNB 「내역」) 응답.
 *
 * <p>목록과 잔액 요약을 한 응답에 묶는다. 화면은 필터를 바꿀 때마다 잔액 카드·목록 건수·리스트를
 * <b>함께</b> 갱신하는데, 따로 두면 같은 조건으로 두 번 호출해야 하고 그 사이에 데이터가 바뀌면
 * 카드와 리스트가 어긋난 채로 보인다.
 *
 * <p>목록 건수는 {@code entries.totalElements} 를 그대로 쓰면 된다.
 */
public record GroupEntryListResponse(
		Summary summary,
		PageResponse<GroupEntrySummaryResponse> entries
) {

	/**
	 * 현재 필터 조건에 해당하는 <b>승인된</b> 내역의 합계. 승인 대기 내역은 잔액에 넣지 않는다.
	 * 조건에 맞는 내역이 없으면 세 값 모두 0 이다.
	 */
	public record Summary(long totalIncome, long totalExpense, long balance) {

		public static Summary of(long totalIncome, long totalExpense) {
			return new Summary(totalIncome, totalExpense, totalIncome - totalExpense);
		}
	}
}
