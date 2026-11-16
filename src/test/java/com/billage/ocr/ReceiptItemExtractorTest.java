package com.billage.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 범용 OCR 줄글에서 품목을 뽑는 규칙 검증.
 *
 * <p>이 기능의 안전장치는 "품목 합이 영수증의 합계와 맞을 때만 믿는다" 하나다. 그래서 잘 뽑는 경우만큼
 * <b>뽑지 말아야 할 때 비우는지</b>를 고정해 둔다 — 틀린 품목이 화면에 채워지면 사용자가 하나씩 지워야 한다.
 */
class ReceiptItemExtractorTest {

	private static List<ReceiptOcrItem> itemsOf(String... lines) {
		return ReceiptTextParser.parse(List.of(lines)).items();
	}

	@Test
	void 수량_단가_금액_순서의_품목을_읽는다() {
		List<ReceiptOcrItem> items = itemsOf(
				"빌리지마트 역삼점",
				"2026-09-21 14:32:10",
				"생수 500ml 2 1,000 2,000",
				"초코과자 1 1,500 1,500",
				"과세물품가액 3,182",
				"부가세 318",
				"합계 3,500");

		assertThat(items).containsExactly(
				new ReceiptOcrItem("생수 500ml", 2, 1_000L, 2_000L, null),
				new ReceiptOcrItem("초코과자", 1, 1_500L, 1_500L, null));
	}

	/** 머리글이 "상품명 단가 수량 금액"인 영수증. 수량·단가 순서가 반대여도 곱으로 가려낸다. */
	@Test
	void 단가_수량_금액_순서여도_수량과_단가를_가려낸다() {
		List<ReceiptOcrItem> items = itemsOf(
				"상품명 단가 수량 금액",
				"아메리카노 4,500 2 9,000",
				"------------------------",
				"합계 9,000");

		assertThat(items).containsExactly(new ReceiptOcrItem("아메리카노", 2, 4_500L, 9_000L, null));
	}

	/** 금액만 찍히는 서식. 수량·단가는 지어내지 않고 비운다. */
	@Test
	void 금액만_있는_품목은_수량과_단가를_비운다() {
		List<ReceiptOcrItem> items = itemsOf(
				"떡볶이 4,000",
				"순대 4,500",
				"합계 8,500");

		assertThat(items).containsExactly(
				new ReceiptOcrItem("떡볶이", null, null, 4_000L, null),
				new ReceiptOcrItem("순대", null, null, 4_500L, null));
	}

	/** 마트 영수증: 이름 한 줄, 그 아래 바코드와 숫자 한 줄. 앞의 "*" 는 면세 표시다. */
	@Test
	void 이름과_숫자가_두_줄로_찍힌_품목을_읽는다() {
		List<ReceiptOcrItem> items = itemsOf(
				"001 *바나나",
				"8801234567890 3,980 1 3,980",
				"002 우유 1L",
				"8809876543210 2,650 2 5,300",
				"합계 9,280");

		assertThat(items).containsExactly(
				new ReceiptOcrItem("바나나", 1, 3_980L, 3_980L, null),
				new ReceiptOcrItem("우유 1L", 2, 2_650L, 5_300L, null));
	}

	/** 품목 사이의 할인 줄은 품목이 아니다. 할인을 뺀 합이 결제금액과 맞으면 믿는다. */
	@Test
	void 할인_줄은_품목에서_빼고_합에_반영한다() {
		List<ReceiptOcrItem> items = itemsOf(
				"생수 2 1,000 2,000",
				"행사할인 -500",
				"과자 1 1,500 1,500",
				"결제금액 3,000");

		assertThat(items).extracting(ReceiptOcrItem::name).containsExactly("생수", "과자");
	}

	@Test
	void 할인_전_소계와_맞아도_믿는다() {
		List<ReceiptOcrItem> items = itemsOf(
				"생수 2 1,000 2,000",
				"과자 1 1,500 1,500",
				"소계 3,500",
				"할인 -500",
				"결제금액 3,000");

		assertThat(items).extracting(ReceiptOcrItem::name).containsExactly("생수", "과자");
	}

	/** 숫자 하나를 잘못 읽으면(1,500 → 1,600) 합이 어긋난다. 틀린 품목을 내보내느니 비운다. */
	@Test
	void 한_품목이라도_잘못_읽어_합이_어긋나면_전부_비운다() {
		assertThat(itemsOf(
				"생수 2 1,000 2,000",
				"과자 1 1,600 1,600",
				"합계 3,500")).isEmpty();
	}

	/** 품목 줄 하나를 놓쳐도(이름 없이 숫자만 남음 등) 합이 어긋나 비운다. */
	@Test
	void 품목을_빠뜨려_합이_모자라면_비운다() {
		assertThat(itemsOf(
				"생수 2 1,000 2,000",
				"2026-09-21 14:32",
				"과자 1 1,500 1,500",
				"합계 3,500")).isEmpty();
	}

	/** 영수증 머리의 주소·사업자번호는 품목표 위에서 멈추므로 섞이지 않는다. */
	@Test
	void 머리글_정보를_품목으로_집지_않는다() {
		List<ReceiptOcrItem> items = itemsOf(
				"빌리지마트 역삼점",
				"서울 강남구 테헤란로 123",
				"사업자번호 123-45-67890",
				"생수 2 1,000 2,000",
				"합계 2,000");

		assertThat(items).extracting(ReceiptOcrItem::name).containsExactly("생수");
	}

	/** 숫자 오인식 보정은 품목 금액에도 적용된다. */
	@Test
	void 품목_금액의_숫자_오인식도_보정한다() {
		List<ReceiptOcrItem> items = itemsOf(
				"생수 2 1.000 2.000",
				"과자 1,5O0",
				"합계 3,500");

		assertThat(items).extracting(ReceiptOcrItem::amount).containsExactly(2_000L, 1_500L);
	}
}
