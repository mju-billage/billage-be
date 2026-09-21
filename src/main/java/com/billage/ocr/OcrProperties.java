package com.billage.ocr;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * 영수증 인식(OCR) 설정.
 *
 * @param provider       인식 수단. 로컬·테스트는 {@code STUB}, dev·prod 는 {@code CLOVA_GENERAL}.
 *                       영수증 특화 모델({@code CLOVA_RECEIPT})은 건당 단가 때문에 쓰지 않기로 했다(2026-09-21).
 * @param invokeUrl      클로바 OCR 도메인의 APIGW Invoke URL. 도메인마다 다르고 <b>주소 끝이 곧 모델</b>이다
 *                       ({@code .../document/receipt} vs {@code .../general}). 도메인을 만들 때 고른 모델이
 *                       정해지므로 끝만 바꿔 쓸 수 없다 — 다른 모델로 부르면 400 {@code Request domain invalid}.
 * @param secretKey      클로바 OCR 도메인 발급 시크릿. {@code X-OCR-SECRET} 헤더로 보낸다.
 *                       S3·SES 와 달리 인스턴스 역할로 해결되지 않는 외부 서비스라 서버에 값이 생긴다 — 환경변수로만 주입한다.
 * @param maxImageSize   인식에 보낼 이미지 상한. 업로드 상한(10MB)보다 작다 — 이미지를 통째로 Base64 로 만들어
 *                       JSON 에 실으므로 원본의 1.4배가 메모리에 올라가는데, 서버가 RAM 1GB 짜리 t3.micro 다.
 * @param maxScansPerHour 사용자당 시간당 인식 횟수. 외부 OCR 은 건당 과금이라 상한이 없으면 비용이 샌다.
 */
@ConfigurationProperties(prefix = "billage.ocr")
public record OcrProperties(
		@DefaultValue("STUB") ProviderType provider,
		@DefaultValue("") String invokeUrl,
		@DefaultValue("") String secretKey,
		@DefaultValue("4MB") DataSize maxImageSize,
		@DefaultValue("3s") Duration connectTimeout,
		@DefaultValue("10s") Duration readTimeout,
		@DefaultValue("60") int maxScansPerHour
) {

	public enum ProviderType {
		/** 고정값 반환. 로컬·테스트 전용이며 배포 환경에서는 기동이 실패한다. */
		STUB,
		/**
		 * 영수증 전용 모델({@code /document/receipt}). 총액·결제일·품목을 필드로 받는다.
		 * <b>쓰지 않는다</b> — 건당 단가 때문에 범용 모델로 확정했다(2026-09-21). 단가 정책이 바뀌면
		 * 이 값으로 되돌릴 수 있게 구현은 남겨 뒀지만, 실제 호출로 검증한 적은 없다.
		 */
		CLOVA_RECEIPT,
		/**
		 * 범용 모델({@code /general}). 글자와 좌표만 돌려주므로 총액·결제일을 {@link ReceiptTextParser} 가
		 * 추론하고 품목은 비운다. <b>이 서비스의 확정 수단이다.</b>
		 */
		CLOVA_GENERAL
	}
}
