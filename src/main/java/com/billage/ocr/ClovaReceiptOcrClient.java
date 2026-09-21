package com.billage.ocr;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 네이버 클라우드 플랫폼 CLOVA OCR 영수증 모델 호출.
 *
 * <p>범용 OCR 이 아니라 영수증 전용 모델을 쓴다 — 범용은 글자 덩어리만 돌려주어 그중 무엇이 총액이고
 * 무엇이 날짜인지 우리가 다시 추론해야 하지만, 영수증 모델은 {@code totalPrice}·{@code paymentInfo.date}·
 * {@code subResults[].items} 로 필드를 구분해 준다. 영수증 파싱 로직을 우리가 떠안지 않으려고 이 모델을 골랐다.
 *
 * <p>응답 JSON 은 우리가 쓰지 않는 필드(좌표·면적·통화 등)가 훨씬 많다. 필요한 것만 record 로 받고
 * 나머지는 무시한다(Boot 기본값이 알 수 없는 속성을 무시한다).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "billage.ocr.provider", havingValue = "CLOVA_RECEIPT")
public class ClovaReceiptOcrClient implements ReceiptOcrClient {

	private static final String INFER_SUCCESS = "SUCCESS";
	/** 영수증을 읽지 못했다는 뜻. 클로바 쪽 장애를 뜻하는 {@code ERROR} 와 다르다. */
	private static final String INFER_FAILURE = "FAILURE";
	/** 금액·수량에 콤마와 통화기호가 섞여 오므로 숫자만 남긴다. */
	private static final String NON_DIGIT = "[^0-9]";
	/** {@code Long.parseLong} 이 넘치지 않을 자리수. 잘못 읽은 긴 숫자를 금액으로 삼지 않으려는 방어선이기도 하다. */
	private static final int MAX_DIGITS = 18;

	private final ClovaHttpClient httpClient;

	public ClovaReceiptOcrClient(OcrProperties properties) {
		this.httpClient = new ClovaHttpClient(properties);
	}

	@Override
	public ReceiptOcrResult recognize(byte[] image, ReceiptImageFormat format) {
		ClovaResponse response = httpClient.post(image, format, ClovaResponse.class);

		if (response == null || response.images() == null || response.images().isEmpty()) {
			log.error("클로바 OCR 응답에 이미지 결과가 없습니다.");
			throw new BusinessException(ErrorCode.OCR_PROCESSING_FAILED);
		}

		ClovaImageResult result = response.images().getFirst();
		if (result == null) {
			// JSON 배열에 null 원소가 들어오면 아래 inferResult() 에서 터진다.
			log.error("클로바 OCR 응답의 이미지 결과가 비어 있습니다.");
			throw new BusinessException(ErrorCode.OCR_PROCESSING_FAILED);
		}
		if (INFER_FAILURE.equals(result.inferResult())) {
			// 흐릿하거나 영수증이 아닌 사진. 장애가 아니라 "읽을 게 없다"이다.
			log.info("클로바 OCR 인식 실패. message={}", result.message());
			throw new BusinessException(ErrorCode.OCR_RESULT_EMPTY);
		}
		if (!INFER_SUCCESS.equals(result.inferResult())) {
			// ERROR(클로바 쪽 처리 실패)와 우리가 모르는 값. 빈 결과로 뭉개면 클로바 장애가
			// "영수증을 못 읽었습니다"로 사용자에게만 보이고 아무도 눈치채지 못한다.
			log.error("클로바 OCR 처리 오류. inferResult={} message={}", result.inferResult(), result.message());
			throw new BusinessException(ErrorCode.OCR_PROCESSING_FAILED);
		}
		return toResult(result.receipt());
	}

	private ReceiptOcrResult toResult(ClovaReceipt receipt) {
		if (receipt == null || receipt.result() == null) {
			throw new BusinessException(ErrorCode.OCR_RESULT_EMPTY);
		}
		ClovaReceiptResult result = receipt.result();

		Long totalAmount = amountOf(result.totalPrice());
		if (totalAmount == null) {
			// 인식은 됐는데 총액을 못 찾았다. 금액 없는 결과로는 내역 폼에 채울 게 없어 빈 결과와 같다.
			log.info("클로바 OCR 응답에서 총액을 찾지 못했습니다.");
			throw new BusinessException(ErrorCode.OCR_RESULT_EMPTY);
		}
		return new ReceiptOcrResult(storeNameOf(result.storeInfo()), dateOf(result.paymentInfo()), totalAmount,
				itemsOf(result.subResults()));
	}

	/**
	 * 품목 목록. 영수증에 품목이 안 찍혔거나 못 읽은 경우가 흔해 비어 있을 수 있다 —
	 * 총액을 읽었으면 품목이 없어도 결과로 인정한다(명세의 {@code items} 는 빈 배열이 된다).
	 */
	private List<ReceiptOcrItem> itemsOf(List<ClovaSubResult> subResults) {
		if (subResults == null) {
			return List.of();
		}
		return subResults.stream()
				.filter(subResult -> subResult != null && subResult.items() != null)
				.flatMap(subResult -> subResult.items().stream())
				.filter(item -> item != null)
				.map(this::toItem)
				// 이름도 금액도 못 읽은 줄은 화면에 띄워 봐야 사용자가 지울 빈 줄이다.
				.filter(item -> item.name() != null || item.amount() != null)
				.toList();
	}

	private ReceiptOcrItem toItem(ClovaItem item) {
		Long unitPrice = item.price() == null ? null : numberOf(item.price().unitPrice());
		Long amount = item.price() == null ? null : numberOf(item.price().price());
		return new ReceiptOcrItem(textOf(item.name()), quantityOf(item.count()), unitPrice, amount,
				confidenceOf(item.name()));
	}

	/** 수량은 금액과 달리 작은 수이므로 int 로 좁힌다. 못 읽으면 비운다(1 로 단정하지 않는다). */
	private Integer quantityOf(ClovaField count) {
		Long value = numberOf(count);
		return (value == null || value > Integer.MAX_VALUE) ? null : value.intValue();
	}

	private Double confidenceOf(ClovaField field) {
		return field == null ? null : field.confidenceScore();
	}

	private Long amountOf(ClovaTotalPrice totalPrice) {
		return totalPrice == null ? null : numberOf(totalPrice.price());
	}

	/**
	 * 숫자 필드 해석. {@code formatted.value}(클로바가 정규화한 값)를 먼저 보고,
	 * 거기서 숫자를 얻지 못하면 {@code text}(영수증에 찍힌 그대로, "₩ 1,234,500")로 넘어간다.
	 *
	 * <p>앞 후보가 비어 있을 때만이 아니라 <b>숫자를 못 줄 때도</b> 넘어가야 한다 —
	 * 정규화에 실패한 {@code formatted.value} 는 비는 대신 "N/A" 같은 문자열로 오기도 하는데,
	 * 거기서 멈추면 {@code text} 에 멀쩡히 찍힌 금액을 두고 "읽을 내용 없음(422)"을 내리게 된다.
	 */
	private Long numberOf(ClovaField field) {
		Long fromFormatted = parseAmount(formattedValue(field));
		return fromFormatted != null ? fromFormatted : parseAmount(textOf(field));
	}

	/** 숫자를 얻지 못하면 null. 호출부가 다음 후보로 넘어갈 수 있게 예외를 던지지 않는다. */
	private Long parseAmount(String raw) {
		if (raw == null) {
			return null;
		}
		String digits = raw.replaceAll(NON_DIGIT, "");
		if (digits.isEmpty() || digits.length() > MAX_DIGITS) {
			return null;
		}
		long value = Long.parseLong(digits);
		// 금액은 항상 양수다(도메인 규칙). 0 은 못 읽은 것과 구분되지 않으므로 비운다.
		return value > 0 ? value : null;
	}

	/**
	 * 결제일. {@code formatted} 가 연·월·일을 나눠 주므로 그것만 쓴다 —
	 * {@code text} 는 영수증마다 표기가 제각각이라 파싱 규칙을 우리가 떠안게 된다.
	 */
	private LocalDate dateOf(ClovaPaymentInfo paymentInfo) {
		if (paymentInfo == null || paymentInfo.date() == null || paymentInfo.date().formatted() == null) {
			return null;
		}
		ClovaFormatted formatted = paymentInfo.date().formatted();
		try {
			return LocalDate.of(Integer.parseInt(formatted.year()), Integer.parseInt(formatted.month()),
					Integer.parseInt(formatted.day()));
		} catch (NumberFormatException | NullPointerException | DateTimeException e) {
			// 날짜는 없어도 된다 — 앱이 오늘 날짜를 기본값으로 둔다. 총액까지 버릴 이유는 없다.
			log.info("클로바 OCR 결제일 해석 실패: {}", paymentInfo.date().text());
			return null;
		}
	}

	private String storeNameOf(ClovaStoreInfo storeInfo) {
		return storeInfo == null ? null : firstNonBlank(formattedValue(storeInfo.name()), textOf(storeInfo.name()));
	}

	private String formattedValue(ClovaField field) {
		return (field == null || field.formatted() == null) ? null : field.formatted().value();
	}

	private String textOf(ClovaField field) {
		return field == null ? null : trimToNull(field.text());
	}

	private String firstNonBlank(String first, String second) {
		String trimmed = trimToNull(first);
		return trimmed != null ? trimmed : trimToNull(second);
	}

	private String trimToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

	/** {@code value} 는 금액·상호·수량, {@code year/month/day} 는 날짜에 쓰인다. 필드마다 채워지는 쪽이 다르다. */
	private record ClovaFormatted(String value, String year, String month, String day) {
	}

	private record ClovaField(String text, ClovaFormatted formatted, Double confidenceScore) {
	}

	private record ClovaStoreInfo(ClovaField name) {
	}

	private record ClovaPaymentInfo(ClovaField date) {
	}

	private record ClovaTotalPrice(ClovaField price) {
	}

	/** 품목 줄의 가격. 바깥 {@code price} 아래 단가와 금액이 한 번 더 들어 있다. */
	private record ClovaItemPrice(ClovaField unitPrice, ClovaField price) {
	}

	private record ClovaItem(ClovaField name, ClovaField count, ClovaItemPrice price) {
	}

	/** 영수증 한 장에 품목 블록이 여러 개로 잘려 올 수 있어 배열이다. 우리는 전부 이어 붙인다. */
	private record ClovaSubResult(List<ClovaItem> items) {
	}

	private record ClovaReceiptResult(ClovaStoreInfo storeInfo, ClovaPaymentInfo paymentInfo,
			ClovaTotalPrice totalPrice, List<ClovaSubResult> subResults) {
	}

	private record ClovaReceipt(ClovaReceiptResult result) {
	}

	private record ClovaImageResult(String inferResult, String message, ClovaReceipt receipt) {
	}

	private record ClovaResponse(List<ClovaImageResult> images) {
	}
}
