package com.billage.ocr.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import com.billage.common.response.KoreanTime;
import com.billage.ocr.ReceiptOcrResult;

/**
 * 영수증 인식 결과 응답.
 *
 * <p>인식 결과는 파일에 붙여 보관한다(V22). 이 파일이 내역의 증빙으로 연결되면 내역 상세의 {@code ocr} 로
 * 같은 모양 그대로 다시 내려간다 — 그때 OCR 을 다시 부르지 않는다.
 *
 * @param merchantName 상호명. 못 읽으면 null.
 * @param purchasedOn  결제일. 못 읽으면 null — 앱이 오늘 날짜를 기본값으로 둔다.
 * @param items        품목 목록. 영수증에 품목이 안 찍혔거나 못 읽으면 빈 배열.
 * @param totalAmount  총 결제 금액(원). 이것만은 항상 채워진다 — 못 읽으면 응답이 아니라 {@code OCR_RESULT_EMPTY} 다.
 * @param recognizedAt 인식 시각.
 */
public record ReceiptOcrResponse(Long fileId, String merchantName, LocalDate purchasedOn,
		List<ReceiptOcrItemResponse> items, long totalAmount, OffsetDateTime recognizedAt) {

	public static ReceiptOcrResponse of(Long fileId, ReceiptOcrResult result, LocalDateTime recognizedAt) {
		return new ReceiptOcrResponse(fileId, result.merchantName(), result.purchasedOn(),
				result.items().stream().map(ReceiptOcrItemResponse::of).toList(),
				result.totalAmount(), KoreanTime.toOffset(recognizedAt));
	}
}
