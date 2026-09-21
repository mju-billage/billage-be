package com.billage.ocr;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.Set;
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

	/** 모델별로 정해진 주소 끝. 도메인을 만들 때 고른 모델이 곧 경로라 바꿔 쓸 수 없다. */
	private static final String RECEIPT_PATH = "/document/receipt";
	private static final String GENERAL_PATH = "/general";

	/** 평문 HTTP 를 허용할 호스트. 테스트가 띄우는 가짜 서버만 해당한다. */
	private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "localhost", "::1", "[::1]");

	ClovaHttpClient(OcrProperties properties) {
		if (properties.invokeUrl() == null || properties.invokeUrl().isBlank()) {
			throw new IllegalStateException("billage.ocr.invoke-url 설정이 필요합니다.");
		}
		if (properties.secretKey() == null || properties.secretKey().isBlank()) {
			throw new IllegalStateException("billage.ocr.secret-key 설정이 필요합니다.");
		}
		requireSecureUrl(properties.invokeUrl());
		requireMatchingModel(properties);
		this.properties = properties;

		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(properties.connectTimeout());
		factory.setReadTimeout(properties.readTimeout());
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	/**
	 * 시크릿을 {@code X-OCR-SECRET} 헤더로 보내므로 평문 HTTP 면 키가 그대로 노출된다.
	 * 테스트가 띄우는 로컬 가짜 서버만 예외로 둔다.
	 */
	private static void requireSecureUrl(String invokeUrl) {
		URI uri;
		try {
			uri = new URI(invokeUrl);
		} catch (URISyntaxException e) {
			throw new IllegalStateException("billage.ocr.invoke-url 이 올바른 주소가 아닙니다: " + invokeUrl, e);
		}
		// 슬래시를 빠뜨린 "https:/general" 은 scheme 이 https 인 채로 host 만 비어 파싱된다.
		// 여기서 걸러 내지 않으면 오타 난 주소로 서버가 멀쩡히 뜨고, 인식할 때가 되어서야 502 가 난다.
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw new IllegalStateException("billage.ocr.invoke-url 에 호스트가 없습니다: " + invokeUrl);
		}
		if ("https".equalsIgnoreCase(uri.getScheme())) {
			return;
		}
		if ("http".equalsIgnoreCase(uri.getScheme()) && LOOPBACK_HOSTS.contains(String.valueOf(uri.getHost()))) {
			return;
		}
		throw new IllegalStateException(
				"billage.ocr.invoke-url 은 HTTPS 여야 합니다(시크릿이 평문으로 나갑니다): " + invokeUrl);
	}

	/**
	 * provider 와 주소 끝이 어긋나면 클로바가 400 {@code Request domain invalid} 를 돌려준다.
	 * 그 실패는 호출 시점에 {@code OCR_PROCESSING_FAILED} 로 보여 설정 실수처럼 보이지 않으므로,
	 * 기동할 때 잡는다 — 실제로 {@code /general} 도메인에 {@code /document/receipt} 로 불러 확인한 오류다.
	 */
	private static void requireMatchingModel(OcrProperties properties) {
		String expected = switch (properties.provider()) {
			case CLOVA_RECEIPT -> RECEIPT_PATH;
			case CLOVA_GENERAL -> GENERAL_PATH;
			// STUB 은 이 클래스를 만들지 않는다.
			default -> throw new IllegalStateException("클로바 호출에 쓸 수 없는 provider 입니다: " + properties.provider());
		};
		if (!properties.invokeUrl().endsWith(expected)) {
			throw new IllegalStateException(
					"billage.ocr.provider=%s 에는 %s 로 끝나는 Invoke URL 이 필요합니다. 도메인을 만들 때 고른 모델이 곧 경로라 끝만 바꿔 쓸 수 없습니다: %s"
							.formatted(properties.provider(), expected, properties.invokeUrl()));
		}
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
