package com.billage.statistics.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 「통계/분석」 화면(ETC-2-PAGE-07-0) 응답.
 *
 * <p>화면이 세 블록으로 이뤄지고 한 화면에서 함께 보이므로 한 응답에 묶는다.
 * 폴더 메인과 더보기 두 곳에서 같은 화면이 열리지만 데이터는 같아 API 는 하나면 된다.
 *
 * <p>모든 집계는 <b>승인된 내역만</b> 반영한다. 분석할 데이터가 없으면 {@code mostActiveLedger} 는 null,
 * 나머지는 비어 있고, 화면이 "아직 분석할 데이터가 없어요!" 를 띄운다.
 */
public record StatisticsResponse(
		MostActiveLedger mostActiveLedger,
		List<BudgetUsage> budgetUsage,
		ExpenseShare expenseShare
) {

	/**
	 * 최근 가장 활발한 장부. 기준은 <b>최근 7일 이내 등록된 내역 수</b>다 —
	 * 발생일이 아니라 등록일이라 "요즘 손이 많이 가는 장부"를 가리킨다.
	 *
	 * @param budgetUsageRate 예산 미설정이면 null. 화면도 이때는 프로그레스 바를 그리지 않는다.
	 */
	public record MostActiveLedger(
			Long ledgerId,
			String name,
			long recentEntryCount,
			long totalIncome,
			long totalExpense,
			Long budget,
			BigDecimal budgetUsageRate
	) {
	}

	/**
	 * 예산 대비 소비 현황. <b>예산이 설정된 장부만</b> 담으며 소진율 내림차순이다.
	 *
	 * <p>화면은 상위 3개만 펼쳐 보이고 나머지는 '펼쳐 보기'로 여는데, 장부 수가 많지 않아
	 * 서버는 전체를 내려 주고 자르는 건 클라이언트에 맡긴다.
	 */
	public record BudgetUsage(
			Long ledgerId,
			String name,
			Long budget,
			long totalExpense,
			BigDecimal budgetUsageRate
	) {
	}

	/**
	 * 장부별 지출 비중.
	 *
	 * <p>상위 4개만 개별로 담고 5위 이하는 전부 합쳐 <b>'기타'</b> 한 항목으로 묶는다({@code ledgerId} 가 null).
	 * 화면이 누적 막대 차트라 조각이 많아지면 읽을 수 없어, 묶는 일을 서버가 한다.
	 */
	public record ExpenseShare(long totalExpense, List<Item> items) {

		public record Item(Long ledgerId, String name, long totalExpense, BigDecimal share) {
		}
	}
}
