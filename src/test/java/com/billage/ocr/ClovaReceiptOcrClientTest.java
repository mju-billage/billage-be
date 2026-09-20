package com.billage.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
 * 클로바 OCR 응답 해석 검증. 우리가 손대지 못하는 외부 JSON 구조에 기대는 코드라
 * 필드가 빠지거나 값이 이상할 때 어떻게 되는지를 테스트로 고정해 둔다.
 *
 * <p>가짜 HTTP 서버는 JDK 내장 {@link HttpServer} 로 띄운다 — 이것 하나 때문에 목 서버 라이브러리를
 * 새로 들이지 않는다.
 */
class ClovaReceiptOcrClientTest {

	private HttpServer server;
	private String invokeUrl;

	private final AtomicReference<String> responseBody = new AtomicReference<>();
	private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
	private final AtomicReference<String> receivedSecret = new AtomicReference<>();

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/document/receipt", exchange -> {
			receivedSecret.set(exchange.getRequestHeaders().getFirst("X-OCR-SECRET"));
			exchange.getRequestBody().readAllBytes();
			byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(responseStatus.get(), body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		server.start();
		invokeUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/document/receipt";
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private ClovaReceiptOcrClient client() {
		return new ClovaReceiptOcrClient(new OcrProperties(OcrProperties.ProviderType.CLOVA_RECEIPT, invokeUrl, "test-secret",
				DataSize.ofMegabytes(4), Duration.ofSeconds(3), Duration.ofSeconds(5), 60));
	}

	private ReceiptOcrResult recognize() {
		return client().recognize("image".getBytes(StandardCharsets.UTF_8), ReceiptImageFormat.JPG);
	}

	@Test
	void 상호와_결제일과_총액과_품목을_읽는다() {
		responseBody.set("""
				{
				  "version": "V2",
				  "images": [{
				    "inferResult": "SUCCESS",
				    "receipt": { "result": {
				      "storeInfo": { "name": { "text": "빌리지마트", "formatted": { "value": "빌리지마트" } } },
				      "paymentInfo": { "date": { "text": "2026-07-20", "formatted": { "year": "2026", "month": "07", "day": "20" } } },
				      "totalPrice": { "price": { "text": "3,500", "formatted": { "value": "3500" } } },
				      "subResults": [{ "items": [
				        { "name": { "text": "생수", "confidenceScore": 0.96 }, "count": { "text": "2" },
				          "price": { "unitPrice": { "text": "1,000" }, "price": { "text": "2,000" } } },
				        { "name": { "text": "과자", "confidenceScore": 0.88 }, "count": { "text": "1" },
				          "price": { "unitPrice": { "text": "1,500" }, "price": { "text": "1,500" } } }
				      ]}]
				    }}
				  }]
				}
				""");

		ReceiptOcrResult result = recognize();

		assertThat(result.merchantName()).isEqualTo("빌리지마트");
		assertThat(result.purchasedOn()).isEqualTo(LocalDate.of(2026, 7, 20));
		assertThat(result.totalAmount()).isEqualTo(3_500L);
		assertThat(result.items()).hasSize(2);
		assertThat(result.items().getFirst())
				.isEqualTo(new ReceiptOcrItem("생수", 2, 1_000L, 2_000L, 0.96));
		assertThat(receivedSecret.get()).isEqualTo("test-secret");
	}

	@Test
	void 금액에_콤마와_통화기호가_섞여_와도_숫자만_남긴다() {
		responseBody.set("""
				{"images": [{
				  "inferResult": "SUCCESS",
				  "receipt": { "result": { "totalPrice": { "price": { "text": "₩ 1,234,500" } } } }
				}]}
				""");

		assertThat(recognize().totalAmount()).isEqualTo(1_234_500L);
	}

	/** 품목 블록은 영수증 한 장이 여러 덩어리로 잘려 오기도 한다. 전부 이어 붙여야 한다. */
	@Test
	void 품목_블록이_여러_개여도_모두_모은다() {
		responseBody.set("""
				{"images": [{
				  "inferResult": "SUCCESS",
				  "receipt": { "result": {
				    "totalPrice": { "price": { "text": "3000" } },
				    "subResults": [
				      { "items": [{ "name": { "text": "생수" }, "price": { "price": { "text": "1000" } } }] },
				      { "items": [{ "name": { "text": "과자" }, "price": { "price": { "text": "2000" } } }] }
				    ]
				  }}
				}]}
				""");

		assertThat(recognize().items()).extracting(ReceiptOcrItem::name).containsExactly("생수", "과자");
	}

	/** 총액만 읽혀도 내역 폼은 쓸모가 있다. 품목이 없다고 결과를 버리면 안 된다. */
	@Test
	void 품목을_못_읽어도_총액이_있으면_결과다() {
		responseBody.set("""
				{"images": [{
				  "inferResult": "SUCCESS",
				  "receipt": { "result": { "totalPrice": { "price": { "formatted": { "value": "8000" } } } } }
				}]}
				""");

		ReceiptOcrResult result = recognize();

		assertThat(result.totalAmount()).isEqualTo(8_000L);
		assertThat(result.items()).isEmpty();
		assertThat(result.merchantName()).isNull();
		assertThat(result.purchasedOn()).isNull();
	}

	/** 못 읽은 수량·단가를 0 이나 1 로 채우면 사용자가 "0원짜리 품목"을 보게 된다. */
	@Test
	void 못_읽은_수량과_단가는_채우지_않고_비운다() {
		responseBody.set("""
				{"images": [{
				  "inferResult": "SUCCESS",
				  "receipt": { "result": {
				    "totalPrice": { "price": { "text": "5000" } },
				    "subResults": [{ "items": [{ "name": { "text": "묶음상품" },
				      "price": { "price": { "text": "5000" } } }] }]
				  }}
				}]}
				""");

		ReceiptOcrItem item = recognize().items().getFirst();

		assertThat(item.quantity()).isNull();
		assertThat(item.unitPrice()).isNull();
		assertThat(item.amount()).isEqualTo(5_000L);
	}

	@Test
	void 날짜를_못_읽어도_총액은_살린다() {
		responseBody.set("""
				{"images": [{
				  "inferResult": "SUCCESS",
				  "receipt": { "result": {
				    "paymentInfo": { "date": { "text": "알아볼 수 없음", "formatted": { "year": "", "month": "", "day": "" } } },
				    "totalPrice": { "price": { "formatted": { "value": "8000" } } }
				  }}
				}]}
				""");

		ReceiptOcrResult result = recognize();

		assertThat(result.totalAmount()).isEqualTo(8_000L);
		assertThat(result.purchasedOn()).isNull();
	}

	@Test
	void 인식하지_못한_사진은_빈_결과다() {
		responseBody.set("""
				{"images": [{"inferResult": "FAILURE", "message": "not a receipt"}]}
				""");

		assertThatThrownBy(this::recognize)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_RESULT_EMPTY);
	}

	/** 총액 없는 결과로는 내역 폼에 채울 게 없다. 인식됐다고 내려보내면 안 된다. */
	@Test
	void 총액을_못_읽으면_빈_결과로_본다() {
		responseBody.set("""
				{"images": [{
				  "inferResult": "SUCCESS",
				  "receipt": { "result": { "storeInfo": { "name": { "text": "빌리지마트" } } } }
				}]}
				""");

		assertThatThrownBy(this::recognize)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_RESULT_EMPTY);
	}

	/**
	 * 클로바가 처리에 실패한 것({@code ERROR})을 "못 읽었다"({@code FAILURE})로 뭉개면,
	 * 클로바 장애가 사용자에게 "영수증 인식 실패"로만 보이고 아무도 눈치채지 못한다.
	 */
	@Test
	void 클로바_처리_오류는_빈_결과가_아니라_처리_실패다() {
		responseBody.set("""
				{"images": [{"inferResult": "ERROR", "message": "internal ocr error"}]}
				""");

		assertThatThrownBy(this::recognize)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_PROCESSING_FAILED);
	}

	/**
	 * 정규화에 실패한 {@code formatted.value} 는 비는 대신 "N/A" 같은 문자열로 오기도 한다.
	 * 거기서 멈추면 {@code text} 에 멀쩡히 찍힌 금액을 두고 422 를 내리게 된다.
	 */
	@Test
	void formatted_가_숫자를_주지_못하면_text_로_넘어간다() {
		responseBody.set("""
				{"images": [{
				  "inferResult": "SUCCESS",
				  "receipt": { "result": {
				    "totalPrice": { "price": { "text": "3,500", "formatted": { "value": "N/A" } } },
				    "subResults": [{ "items": [{ "name": { "text": "생수" },
				      "count": { "text": "2", "formatted": { "value": "-" } },
				      "price": { "price": { "text": "3,500", "formatted": { "value": "" } } } }] }]
				  }}
				}]}
				""");

		ReceiptOcrResult result = recognize();

		assertThat(result.totalAmount()).isEqualTo(3_500L);
		assertThat(result.items().getFirst().quantity()).isEqualTo(2);
		assertThat(result.items().getFirst().amount()).isEqualTo(3_500L);
	}

	/** 호출 실패를 빈 결과로 뭉개면 키 만료·장애를 아무도 눈치채지 못한다. */
	@Test
	void 호출이_실패하면_빈_결과가_아니라_처리_실패다() {
		responseStatus.set(500);
		responseBody.set("{\"message\":\"internal error\"}");

		assertThatThrownBy(this::recognize)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_PROCESSING_FAILED);
	}
}
