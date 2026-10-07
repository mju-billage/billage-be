package com.billage.ocr;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보관된 영수증 인식 결과. 파일 하나에 한 건이며 다시 인식하면 덮어쓴다.
 *
 * <p>파일과는 ID 로만 이어 둔다. 파일을 지우면 DB 가 이 행을 함께 지운다(ON DELETE CASCADE) —
 * 파일 삭제 경로가 여럿이라, 코드에서 일일이 챙기면 하나라도 빠졌을 때 주인 없는 결과가 남는다.
 */
@Entity
@Table(name = "receipt_ocr")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptOcr {

	static final int TEXT_LENGTH = 255;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "file_id", nullable = false, unique = true, updatable = false)
	private Long fileId;

	@Column(name = "merchant_name", length = TEXT_LENGTH)
	private String merchantName;

	@Column(name = "purchased_on")
	private LocalDate purchasedOn;

	@Column(name = "total_amount", nullable = false)
	private Long totalAmount;

	@Column(name = "recognized_at", nullable = false)
	private LocalDateTime recognizedAt;

	@ElementCollection
	@CollectionTable(name = "receipt_ocr_item", joinColumns = @JoinColumn(name = "receipt_ocr_id"))
	@OrderColumn(name = "line_no")
	private List<ReceiptOcrLine> items = new ArrayList<>();

	private ReceiptOcr(Long fileId) {
		this.fileId = fileId;
	}

	static ReceiptOcr of(Long fileId, ReceiptOcrResult result, LocalDateTime recognizedAt) {
		ReceiptOcr ocr = new ReceiptOcr(fileId);
		ocr.overwrite(result, recognizedAt);
		return ocr;
	}

	/** 같은 파일을 다시 인식했을 때. 이전 결과는 남기지 않는다. */
	void overwrite(ReceiptOcrResult result, LocalDateTime recognizedAt) {
		this.merchantName = fit(result.merchantName());
		this.purchasedOn = result.purchasedOn();
		this.totalAmount = result.totalAmount();
		this.recognizedAt = recognizedAt;
		this.items.clear();
		result.items().stream().map(ReceiptOcrLine::of).forEach(this.items::add);
	}

	ReceiptOcrResult toResult() {
		return new ReceiptOcrResult(merchantName, purchasedOn, totalAmount,
				items.stream().map(ReceiptOcrLine::toItem).toList());
	}

	/** 영수증에서 읽은 글자는 길이를 믿을 수 없다. 컬럼보다 길면 잘라 저장한다 — 저장 실패로 인식 자체를 버리지 않는다. */
	static String fit(String text) {
		return text == null || text.length() <= TEXT_LENGTH ? text : text.substring(0, TEXT_LENGTH);
	}
}
