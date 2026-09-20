package com.billage.ocr;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 클로바 OCR 호출 공통부. 영수증 모델과 범용 모델은 응답만 다르고 요청·인증·타임아웃은 같다.
 *
 * <p>타임아웃을 두지 않으면 클로바 응답이 늦을 때 요청 스레드가 무한정 묶인다
 * (소셜 로그인의 {@code SocialHttpClient} 와 같은 이유). OCR 은 로그인보다 느려서 읽기 제한을 길게 잡는다.
 */
@Slf4j
final class ClovaHttpClient {

	private static final String API_VERSION = "V2";
	private static final String SECRET_HEADER = "X-OCR-SECRET";
	private static final String IMAGE_NAME = "receipt";

	private final RestClient restClient;
	private final OcrProperties properties;

	ClovaHttpClient(OcrProperties properties) {
		if (properties.invokeUrl() == null || properties.invokeUrl().isBlank()) {
			throw new IllegalStateException("billage.ocr.invoke-url 설정이 필요합니다.");
		}
		if (properties.secretKey() == null || properties.secretKey().isBlank()) {
			throw new IllegalStateException("billage.ocr.secret-key 설정이 필요합니다.");
		}
		this.properties = properties;

		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(properties.connectTimeout());
		factory.setReadTimeout(properties.readTimeout());
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	/**
	 * 이미지를 보내고 응답을 {@code responseType} 으로 받는다.
	 *
	 * @throws BusinessException 호출 자체가 실패하면 {@code OCR_PROCESSING_FAILED} — 인식 실패와 구분해야
	 *                           키 만료·장애가 "영수증이 안 읽혔다"로 묻히지 않는다.
	 */
	<T> T post(byte[] image, ReceiptImageFormat format, Class<T> responseType) {
		try {
			return restClient.post()
					.uri(properties.invokeUrl())
					.header(SECRET_HEADER, properties.secretKey())
					.contentType(MediaType.APPLICATION_JSON)
					.body(new ClovaRequest(API_VERSION, UUID.randomUUID().toString(), System.currentTimeMillis(),
							List.of(new ClovaImage(format.value(), IMAGE_NAME,
									Base64.getEncoder().encodeToString(image)))))
					.retrieve()
					.body(responseType);
		} catch (RestClientException e) {
			log.error("클로바 OCR 호출 실패", e);
			throw new BusinessException(ErrorCode.OCR_PROCESSING_FAILED);
		}
	}

	private record ClovaImage(String format, String name, String data) {
	}

	private record ClovaRequest(String version, String requestId, long timestamp, List<ClovaImage> images) {
	}
}
