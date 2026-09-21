package com.billage.ocr;

import java.time.LocalDate;
import java.util.List;

/**
 * 영수증 인식 결과.
 *
 * <p>총액({@code totalAmount})만 필수다. 상호·결제일·품목은 못 읽어도 결과를 돌려준다 —
 * 금액만 채워져도 내역 등록 폼은 쓸모가 있고, 나머지는 사용자가 채운다.
 * 총액마저 못 읽으면 결과가 아니라 {@code OCR_RESULT_EMPTY(422)} 다.
 */
public record ReceiptOcrResult(String merchantName, LocalDate purchasedOn, long totalAmount,
		List<ReceiptOcrItem> items) {
}
