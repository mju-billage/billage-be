package com.billage.ocr;

/**
 * 영수증 인식 제공자. 클로바({@link ClovaReceiptOcrClient})와 스텁({@link StubReceiptOcrClient}) 구현이 있으며
 * {@code billage.ocr.provider} 설정으로 선택한다({@code FileStorage}·{@code MailSender} 와 같은 방식).
 */
public interface ReceiptOcrClient {

	/**
	 * 이미지에서 상호·결제일·총액·품목을 읽는다.
	 *
	 * @throws com.billage.common.exception.BusinessException 읽을 내용이 없으면 {@code OCR_RESULT_EMPTY},
	 *                                                        호출 자체가 실패하면 {@code OCR_PROCESSING_FAILED}.
	 *                                                        둘을 구분하지 않으면 키 만료·장애가 "영수증이 안 읽혔다"로 묻힌다.
	 */
	ReceiptOcrResult recognize(byte[] image, ReceiptImageFormat format);
}
