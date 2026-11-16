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

	/** 마트 영수증의 금액 뒤 표시("*" 할인 적용, "#" 면세). 떼고 읽는다. */
	@Test
	void 금액_뒤에_붙은_표시를_떼고_읽는다() {
		List<ReceiptOcrItem> items = itemsOf(
				"001 아침에우유 900ml",
				"8801069416540 1,980 2 3,960*#",
				"002 햇 무 1개",
				"22008824 1,980 2 3,960 #",
				"합계 7,920");

		assertThat(items).extracting(ReceiptOcrItem::amount).containsExactly(3_960L, 3_960L);
	}

	/** 맨 위 안내문의 "면세"를 합계 묶음으로 보지 않는다. 품목표 머리글 아래에서만 찾는다. */
	@Test
	void 머리글_위의_안내문은_합계_묶음이_아니다() {
		List<ReceiptOcrItem> items = itemsOf(
				"*행사상품 및 면세상품 일부는",
				"포인트 적립이 제외됩니다",
				"NO. 상품명 단가 수량 금액",
				"001 스팸라이트 120g",
				"8801392049187 990 5 4,950*",
				"(#)면세물품: 0",
				"합 계: 4,950");

		assertThat(items).containsExactly(new ReceiptOcrItem("스팸라이트 120g", 5, 990L, 4_950L, null));
	}

	/** 카드 앱 영수증: 품목표와 합계 사이에 "[결제 내역]" 소제목이 끼고, 0원 옵션 줄이 섞인다. */
	@Test
	void 합계_위의_소제목과_0원_옵션_줄을_넘긴다() {
		List<ReceiptOcrItem> items = itemsOf(
				"상품명 단가 수량 금액",
				"바닐라라떼 4,200 1 4,200",
				"- ICE 0 1 0",
				"[결제 내역]",
				"공급가액 3,818원",
				"부가세 382원",
				"결제금액 4,200원");

		assertThat(items).containsExactly(new ReceiptOcrItem("바닐라라떼", 1, 4_200L, 4_200L, null));
	}

	/** 소제목은 한 줄만 넘긴다. 품목표가 아닌 줄이 더 이어지면 품목을 찾지 않는다. */
	@Test
	void 합계_위에_숫자_없는_줄이_여럿이면_품목을_찾지_않는다() {
		List<ReceiptOcrItem> items = itemsOf(
				"아메리카노 4,500",
				"감사합니다",
				"또 오세요",
				"합계 4,500");

		assertThat(items).isEmpty();
	}

	/** 품목 없는 카드전표의 결제 정보 줄은 이름·금액이 있고 금액이 합계와 같아도 품목이 아니다. */
	@Test
	void 결제_정보_줄을_품목으로_집지_않는다() {
		List<ReceiptOcrItem> items = itemsOf(
				"카드매출전표",
				"결제방법 일시불 12,000",
				"합계 12,000");

		assertThat(items).isEmpty();
	}

	/** 중간 소계 위만 읽으면 합이 소계와 맞아도 뒤쪽 품목이 빠져 있다. 최종 금액보다 작은 합계는 믿지 않는다. */
	@Test
	void 최종_금액보다_작은_소계와_맞으면_비운다() {
		List<ReceiptOcrItem> items = itemsOf(
				"상품명 금액",
				"음료 2,000",
				"소계 2,000",
				"빵 3,000",
				"합계 5,000");

		assertThat(items).isEmpty();
	}
}
