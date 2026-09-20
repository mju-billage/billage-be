package com.billage.ocr.dto;

import com.billage.ocr.ReceiptOcrItem;

/**
 * 영수증에서 읽은 품목 한 줄.
 *
 * <p>{@code name} 을 뺀 나머지는 null 일 수 있다 — 영수증마다 인쇄 형식이 달라 수량이나 단가가 없는 줄이 흔하다.
 * 못 읽은 칸을 0 으로 채우면 화면에서 "0원짜리 품목"으로 보이므로 비운 채 내린다.
 *
 * @param confidence 인식 신뢰도(0~1). 품목명 기준이며 화면 노출 여부는 프론트가 정한다.
 */
public record ReceiptOcrItemResponse(String name, Integer quantity, Long unitPrice, Long amount, Double confidence) {

	public static ReceiptOcrItemResponse of(ReceiptOcrItem item) {
		return new ReceiptOcrItemResponse(item.name(), item.quantity(), item.unitPrice(), item.amount(),
				item.confidence());
	}
}
