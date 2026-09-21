package com.billage.ocr;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

/**
 * OCR 에 보낼 수 있는 이미지 형식. 업로드가 허용하는 형식과 같지 않다 —
 * 업로드는 webp 를 받지만 클로바 OCR 은 받지 않는다. 그래서 변환 가능한 것만 여기 둔다.
 */
public enum ReceiptImageFormat {

	JPG,
	PNG;

	/** 클로바 요청의 {@code images[].format} 값. */
	public String value() {
		return name().toLowerCase();
	}

	public static ReceiptImageFormat from(String contentType) {
		if (contentType == null) {
			throw new BusinessException(ErrorCode.INVALID_OCR_FILE);
		}
		return switch (contentType.toLowerCase()) {
			// image/jpg 는 표준 MIME 이 아니지만 일부 RN 이미지 피커가 그렇게 보낸다(FileProperties 참고).
			case "image/jpeg", "image/jpg" -> JPG;
			case "image/png" -> PNG;
			default -> throw new BusinessException(ErrorCode.INVALID_OCR_FILE);
		};
	}
}
