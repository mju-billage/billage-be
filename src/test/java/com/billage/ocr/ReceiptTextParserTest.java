package com.billage.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

/**
 * 범용 OCR 줄글에서 값을 추려내는 규칙 검증.
 *
 * <p>규칙으로 영수증을 읽는 코드라 "어디까지 맞히고 어디서 포기하는지"가 곧 명세다.
 * 특히 <b>잘못 집는 경우</b>(부가세를 총액으로, 사업자번호를 날짜로)를 고정해 둔다 —
 * 틀린 금액을 자신 있게 채우는 것이 못 읽는 것보다 나쁘다.
 */
class ReceiptTextParserTest {

	@Test
	void 상호와_날짜와_합계를_읽는다() {
		ReceiptOcrResult result = ReceiptTextParser.parse(List.of(
				"빌리지마트 역삼점",
				"사업자번호 123-45-67890",
				"2026-09-21 14:32:10",
				"생수 500ml 2 1,000 2,000",
				"합계 6,500"));

		assertThat(result.merchantName()).isEqualTo("빌리지마트 역삼점");
		assertThat(result.purchasedOn()).isEqualTo(LocalDate.of(2026, 9, 21));
		assertThat(result.totalAmount()).isEqualTo(6_500L);
	}

	/** 총액 바로 위에 붙는 구성 요소들. 이걸 총액으로 집으면 장부에 틀린 금액이 올라간다. */
	@Test
	void 과세물품가액과_부가세를_총액으로_집지_않는다() {
		ReceiptOcrResult result = ReceiptTextParser.parse(List.of(
				"과세물품가액 5,909",
				"부가세 591",
				"합계 6,500"));

		assertThat(result.totalAmount()).isEqualTo(6_500L);
	}

	/** "합 계"처럼 띄어 찍히는 영수증이 흔하다. */
	@Test
	void 띄어_찍힌_키워드도_같게_본다() {
		assertThat(ReceiptTextParser.parse(List.of("합    계        6,500")).totalAmount()).isEqualTo(6_500L);
	}

	/** 영수증은 아래쪽에 최종 금액을 한 번 더 찍는다. 뒤에 나온 것이 최종이다. */
	@Test
	void 총액_키워드가_여러_번이면_마지막_것을_쓴다() {
		ReceiptOcrResult result = ReceiptTextParser.parse(List.of(
				"합계 10,000",
				"할인 -2,000",
				"결제금액 8,000"));

		assertThat(result.totalAmount()).isEqualTo(8_000L);
	}

	/** 금액은 오른쪽 열에 찍힌다. 줄 안에서 마지막 숫자가 금액이다. */
	@Test
	void 총액_줄에_숫자가_여럿이면_가장_오른쪽을_쓴다() {
		assertThat(ReceiptTextParser.parse(List.of("합계 3 개 6,500")).totalAmount()).isEqualTo(6_500L);
	}

	/** 사업자번호는 날짜 모양을 흉내 내지만 월이 45 다. 달력에 없는 날짜는 버려야 한다. */
	@Test
	void 사업자번호를_날짜로_읽지_않는다() {
		ReceiptOcrResult result = ReceiptTextParser.parse(List.of(
				"사업자번호 123-45-67890",
				"합계 6,500"));

		assertThat(result.purchasedOn()).isNull();
	}

	@Test
	void 점으로_구분한_날짜와_두자리_연도도_읽는다() {
		assertThat(ReceiptTextParser.parse(List.of("2026.09.21", "합계 100")).purchasedOn())
				.isEqualTo(LocalDate.of(2026, 9, 21));
		assertThat(ReceiptTextParser.parse(List.of("26.09.21", "합계 100")).purchasedOn())
				.isEqualTo(LocalDate.of(2026, 9, 21));
	}

	@Test
	void 전화번호는_날짜가_아니다() {
		assertThat(ReceiptTextParser.parse(List.of("TEL: 02-1234-5678", "합계 100")).purchasedOn()).isNull();
	}

	/** 머리글에는 상호 말고도 사업자번호·주소가 섞인다. */
	@Test
	void 사업자번호_줄을_상호로_집지_않는다() {
		ReceiptOcrResult result = ReceiptTextParser.parse(List.of(
				"사업자번호 123-45-67890",
				"빌리지마트 역삼점",
				"합계 6,500"));

		assertThat(result.merchantName()).isEqualTo("빌리지마트 역삼점");
	}

	/** 총액이 없으면 화면에 채울 게 없다. 다른 값이 보여도 결과로 인정하지 않는다. */
	@Test
	void 총액을_못_찾으면_빈_결과다() {
		assertThatThrownBy(() -> ReceiptTextParser.parse(List.of("빌리지마트", "2026-09-21", "생수 2,000")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_RESULT_EMPTY);
	}

	/** 범용 모델로는 품목을 뽑지 않는다 — 잘못 뽑은 품목은 없는 것보다 나쁘다. */
	@Test
	void 품목은_비워서_돌려준다() {
		assertThat(ReceiptTextParser.parse(List.of("생수 500ml 2 1,000 2,000", "합계 6,500")).items()).isEmpty();
	}
}
