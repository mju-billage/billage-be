package com.billage.ocr;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * 영수증 인식(OCR) 설정.
 *
 * @param provider       인식 수단. 로컬·테스트는 {@code STUB}, dev·prod 는 {@code CLOVA}.
 * @param invokeUrl      클로바 OCR 영수증 모델의 APIGW Invoke URL. 도메인마다 다르며
 *                       {@code https://{...}.apigw.ntruss.com/custom/v1/{도메인ID}/{키}/document/receipt} 형태다.
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
		STUB,
		CLOVA
	}
}
