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

	/** 택시 영수증은 "요금" 한 줄이 총액이다. 합계 같은 말이 없을 때만 본다. */
	@Test
	void 총액_키워드가_없으면_요금_줄을_총액으로_쓴다() {
		ReceiptOcrResult result = ReceiptTextParser.parse(List.of(
				"승차/ 기타: 10,900원 / 0원",
				"요 금: 10,900원",
				"승인 번호: 84462387 / 우리카드"));

		assertThat(result.totalAmount()).isEqualTo(10_900L);
	}

	/** "주차요금" 같은 품목 줄이 합계보다 아래 있어도 합계를 덮어쓰지 않는다. */
	@Test
	void 합계가_있으면_요금_줄은_총액이_아니다() {
		ReceiptOcrResult result = ReceiptTextParser.parse(List.of(
				"합계 12,000",
				"주차요금 2시간 6,000"));

		assertThat(result.totalAmount()).isEqualTo(12_000L);
	}

	/** 합계 줄은 있는데 금액이 가려졌으면 요금 줄로 넘어가지 않는다. */
	@Test
	void 합계_금액이_가려지면_요금_줄을_총액으로_쓰지_않는다() {
		assertThatThrownBy(() -> ReceiptTextParser.parse(List.of(
				"요금 10,000",
				"추가요금 2,000",
				"합계")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_RESULT_EMPTY);
	}

	/** 개수와 금액 합계가 한 줄에 찍히면 금액 쪽을 총액으로 읽는다. */
	@Test
	void 수량과_금액_합계가_한_줄이면_금액을_읽는다() {
		assertThat(ReceiptTextParser.parse(List.of("총수량 3 합계 15,000")).totalAmount()).isEqualTo(15_000L);
	}

	/** 개수 표기를 알아보지 못한 서식("합계수량(EA) 3")은 줄을 통째로 버린다. 앞의 합계를 3원으로 덮어쓰지 않는다. */
	@Test
	void 알아보지_못한_개수_표기가_있으면_총액_줄이_아니다() {
		assertThat(ReceiptTextParser.parse(List.of("합계 15,000", "합계수량(EA) 3")).totalAmount()).isEqualTo(15_000L);
		assertThat(ReceiptTextParser.parse(List.of("결제금액 8,000", "수량 합계(개) 2")).totalAmount()).isEqualTo(8_000L);
	}

	/** 개수만 세는 합계 줄은 총액이 아니다. */
	@Test
	void 수량만_센_합계_줄은_총액이_아니다() {
		assertThat(ReceiptTextParser.parse(List.of("합계 15,000", "총 수량 합계 3")).totalAmount()).isEqualTo(15_000L);
	}

	/** 합계 줄이 가려진 영수증. 개별 요금을 총액으로 채우지 않고 못 읽었다고 한다. */
	@Test
	void 합계가_가려지면_개별_요금_줄을_총액으로_쓰지_않는다() {
		assertThatThrownBy(() -> ReceiptTextParser.parse(List.of(
				"기본요금 3,800",
				"주차요금 2시간 6,000")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_RESULT_EMPTY);
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

	/** 품목 합이 합계와 어긋나면 품목을 믿지 않는다 — 잘못 뽑은 품목은 없는 것보다 나쁘다. */
	@Test
	void 품목_합이_합계와_다르면_품목을_비운다() {
		assertThat(ReceiptTextParser.parse(List.of("생수 500ml 2 1,000 2,000", "합계 6,500")).items()).isEmpty();
	}

	// ---- 범용 OCR 의 숫자 오인식. 그대로 읽으면 "15" 만 남아 15원이 장부에 올라간다. ----

	@Test
	void 쉼표를_점으로_읽어도_금액을_읽는다() {
		assertThat(ReceiptTextParser.parse(List.of("합계 15.000")).totalAmount()).isEqualTo(15_000L);
		assertThat(ReceiptTextParser.parse(List.of("합계 1.500.000")).totalAmount()).isEqualTo(1_500_000L);
	}

	@Test
	void 쉼표_뒤에_공백이_끼어도_금액을_읽는다() {
		assertThat(ReceiptTextParser.parse(List.of("합계 15, 000")).totalAmount()).isEqualTo(15_000L);
	}

	@Test
	void 숫자_0을_O로_읽어도_금액을_읽는다() {
		assertThat(ReceiptTextParser.parse(List.of("합계 15,O00원")).totalAmount()).isEqualTo(15_000L);
	}

	/** 점으로 구분한 날짜를 금액 보정이 망가뜨리지 않는다. */
	@Test
	void 금액_보정이_날짜를_건드리지_않는다() {
		assertThat(ReceiptTextParser.parse(List.of("2026.09.21", "합계 15.000")).purchasedOn())
				.isEqualTo(LocalDate.of(2026, 9, 21));
	}

	/** 승인번호·카드번호는 금액 열 오른쪽에 붙어 나오기도 한다. 집으면 금액이 수천 배로 뛴다. */
	@Test
	void 승인번호를_총액으로_집지_않는다() {
		ReceiptOcrResult result = ReceiptTextParser.parse(List.of(
				"합계 15,000",
				"결제금액 15,000 승인번호 30012345"));

		assertThat(result.totalAmount()).isEqualTo(15_000L);
	}

	/** 개수를 세는 줄이 맨 아래 찍히면 "마지막 줄" 규칙에 3원이 총액이 된다. */
	@Test
	void 수량_합계_줄을_총액으로_집지_않는다() {
		ReceiptOcrResult result = ReceiptTextParser.parse(List.of(
				"합계 15,000",
				"총 수량 합계 3"));

		assertThat(result.totalAmount()).isEqualTo(15_000L);
	}

	/** "부가세"가 총액을 꾸미는 말로 쓰이면 총액 줄이다. */
	@Test
	void 부가세포함_합계는_총액이다() {
		ReceiptOcrResult result = ReceiptTextParser.parse(List.of(
				"과세물품 13,636",
				"부가세 1,364",
				"부가세포함 합계 15,000"));

		assertThat(result.totalAmount()).isEqualTo(15_000L);
	}
}
