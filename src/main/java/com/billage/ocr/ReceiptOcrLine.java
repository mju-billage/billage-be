package com.billage.ocr;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 보관된 인식 결과의 품목 한 줄. {@link ReceiptOcrItem} 을 그대로 저장한 것이다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptOcrLine {

	@Column(length = ReceiptOcr.TEXT_LENGTH)
	private String name;

	private Integer quantity;

	@Column(name = "unit_price")
	private Long unitPrice;

	private Long amount;

	private Double confidence;

	private ReceiptOcrLine(String name, Integer quantity, Long unitPrice, Long amount, Double confidence) {
		this.name = name;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.amount = amount;
		this.confidence = confidence;
	}

	static ReceiptOcrLine of(ReceiptOcrItem item) {
		return new ReceiptOcrLine(ReceiptOcr.fit(item.name()), item.quantity(), item.unitPrice(), item.amount(),
				item.confidence());
	}

	ReceiptOcrItem toItem() {
		return new ReceiptOcrItem(name, quantity, unitPrice, amount, confidence);
	}
}
