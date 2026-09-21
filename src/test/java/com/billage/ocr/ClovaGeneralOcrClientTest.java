package com.billage.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;

/**
 * 범용 OCR 모델 연동 검증.
 *
 * <p>핵심 케이스는 {@code clova-general-receipt-response.json} 이다 — 지어낸 JSON 이 아니라
 * <b>실제 CLOVA 범용 OCR 에 영수증 이미지를 넣고 받은 응답</b>을 그대로 넣어 두었다.
 * 이 구현의 위험은 응답 형식이 아니라 "좌표로 줄을 되돌리는 것"에 있어서, 진짜 좌표로 검증해야 의미가 있다.
 * (그 응답에서 {@code lineBreak} 는 "합계"와 "6,500" 을 다른 묶음으로 갈라놓았다.)
 */
class ClovaGeneralOcrClientTest {

	private HttpServer server;
	private String invokeUrl;

	private final AtomicReference<String> responseBody = new AtomicReference<>();
	private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/general", exchange -> {
			exchange.getRequestBody().readAllBytes();
			byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(responseStatus.get(), body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		server.start();
		invokeUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/general";
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private ReceiptOcrResult recognize() {
		ClovaGeneralOcrClient client = new ClovaGeneralOcrClient(
				new OcrProperties(OcrProperties.ProviderType.CLOVA_GENERAL, invokeUrl, "test-secret",
						DataSize.ofMegabytes(4), Duration.ofSeconds(3), Duration.ofSeconds(5), 60));
		return client.recognize("image".getBytes(StandardCharsets.UTF_8), ReceiptImageFormat.PNG);
	}

	@Test
	void 실제_범용OCR_응답에서_상호와_날짜와_총액을_읽는다() throws IOException {
		responseBody.set(Files.readString(Path.of("src/test/resources/ocr/clova-general-receipt-response.json")));

		ReceiptOcrResult result = recognize();

		assertThat(result.merchantName()).isEqualTo("빌리지마트 역삼점");
		assertThat(result.purchasedOn()).isEqualTo(LocalDate.of(2026, 9, 21));
		// 영수증의 "합 계 6,500" — 바로 위의 과세물품가액(5,909)·부가세(591)에 속지 않아야 한다.
		assertThat(result.totalAmount()).isEqualTo(6_500L);
		assertThat(result.items()).isEmpty();
	}

	@Test
	void 글자를_하나도_못_읽으면_빈_결과다() {
		responseBody.set("""
				{"images": [{"inferResult": "SUCCESS", "fields": []}]}
				""");

		assertThatThrownBy(this::recognize)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_RESULT_EMPTY);
	}

	/**
	 * {@code ERROR} 는 클로바 쪽 장애다. 입력 이미지 문제로 오는 {@code ERROR}(해상도 초과 등)는
	 * {@link ReceiptImageDimensions} 가 보내기 전에 걸러 400 으로 돌려주므로 여기까지 오지 않는다.
	 */
	@Test
	void 클로바_처리_오류는_처리_실패다() {
		responseBody.set("""
				{"images": [{"inferResult": "ERROR", "message": "Internal server error"}]}
				""");

		assertThatThrownBy(this::recognize)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_PROCESSING_FAILED);
	}

	/** JSON 배열에 null 원소가 들어오면 inferResult() 에서 터진다. */
	@Test
	void 이미지_결과가_null_이어도_터지지_않는다() {
		responseBody.set("{\"images\": [null]}");

		assertThatThrownBy(this::recognize)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_PROCESSING_FAILED);
	}

	@Test
	void 호출이_실패하면_처리_실패다() {
		responseStatus.set(500);
		responseBody.set("{\"message\":\"internal error\"}");

		assertThatThrownBy(this::recognize)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_PROCESSING_FAILED);
	}
}
