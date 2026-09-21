package com.billage.ocr;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * 기동 시점 설정 검증. 이 판정들은 <b>잘못된 설정으로 서버가 뜨지 않게</b> 하는 것이 목적이라,
 * 호출 시점이 아니라 생성자에서 터져야 한다.
 */
class ClovaHttpClientTest {

	private OcrProperties properties(OcrProperties.ProviderType provider, String invokeUrl) {
		return new OcrProperties(provider, invokeUrl, "secret", DataSize.ofMegabytes(4),
				Duration.ofSeconds(3), Duration.ofSeconds(10), 60);
	}

	/** 시크릿을 헤더로 보내므로 평문 HTTP 면 키가 그대로 노출된다. */
	@Test
	void 평문_HTTP_주소는_기동을_막는다() {
		assertThatThrownBy(() -> new ClovaHttpClient(
				properties(OcrProperties.ProviderType.CLOVA_GENERAL, "http://ocr.example.com/general")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("HTTPS");
	}

	@Test
	void HTTPS_주소는_통과한다() {
		assertThatCode(() -> new ClovaHttpClient(
				properties(OcrProperties.ProviderType.CLOVA_GENERAL, "https://ocr.example.com/general")))
				.doesNotThrowAnyException();
	}

	/** 테스트가 띄우는 가짜 서버는 평문이다. 이것까지 막으면 연동 테스트를 못 쓴다. */
	@Test
	void 로컬_가짜_서버는_평문을_허용한다() {
		assertThatCode(() -> new ClovaHttpClient(
				properties(OcrProperties.ProviderType.CLOVA_GENERAL, "http://127.0.0.1:8080/general")))
				.doesNotThrowAnyException();
	}

	/**
	 * provider 와 주소 끝이 어긋나면 클로바가 400 Request domain invalid 를 돌려준다(실제로 확인).
	 * 그 실패는 호출 시점에 502 로 보여 설정 실수처럼 보이지 않으므로 기동할 때 잡는다.
	 */
	@Test
	void provider_와_주소_끝이_어긋나면_기동을_막는다() {
		assertThatThrownBy(() -> new ClovaHttpClient(
				properties(OcrProperties.ProviderType.CLOVA_RECEIPT, "https://ocr.example.com/general")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("/document/receipt");

		assertThatThrownBy(() -> new ClovaHttpClient(
				properties(OcrProperties.ProviderType.CLOVA_GENERAL, "https://ocr.example.com/document/receipt")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("/general");
	}

	@Test
	void 짝이_맞으면_통과한다() {
		assertThatCode(() -> new ClovaHttpClient(
				properties(OcrProperties.ProviderType.CLOVA_RECEIPT, "https://ocr.example.com/document/receipt")))
				.doesNotThrowAnyException();
	}
}
