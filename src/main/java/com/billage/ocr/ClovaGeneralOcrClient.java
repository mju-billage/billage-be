package com.billage.ocr;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * CLOVA OCR <b>범용</b> 모델 호출. 영수증 모델 도메인 승인을 기다리는 동안 쓰는 임시 구현이다.
 *
 * <p>범용 모델은 글자와 좌표만 돌려준다 — 무엇이 총액이고 무엇이 날짜인지는 우리가 추론해야 한다
 * ({@link ReceiptLineAssembler} 로 줄을 되돌리고 {@link ReceiptTextParser} 가 값을 추려낸다).
 * 정확히 그 일을 떠안지 않으려고 영수증 모델을 골랐던 것이므로, <b>영수증 도메인이 승인되면
 * {@code billage.ocr.provider} 를 {@code CLOVA_RECEIPT} 로 바꾸고 이 클래스와 파서를 지운다.</b>
 *
 * <p>품목({@code items})은 항상 비어 있다. 범용 OCR 로 품목 행만 가려내려면 열 위치까지 추론해야 하는데,
 * 잘못 뽑은 품목은 없는 것보다 나쁘다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "billage.ocr.provider", havingValue = "CLOVA_GENERAL")
public class ClovaGeneralOcrClient implements ReceiptOcrClient {

	private static final String INFER_SUCCESS = "SUCCESS";
	private static final String INFER_FAILURE = "FAILURE";

	private final ClovaHttpClient httpClient;

	public ClovaGeneralOcrClient(OcrProperties properties) {
		this.httpClient = new ClovaHttpClient(properties);
		log.warn("영수증 인식이 범용 OCR 모델로 동작합니다(임시). 총액·결제일은 추론값이고 품목은 비어 있습니다. "
				+ "영수증 모델 도메인이 준비되면 billage.ocr.provider=CLOVA_RECEIPT 로 바꾸세요.");
	}

	@Override
	public ReceiptOcrResult recognize(byte[] image, ReceiptImageFormat format) {
		GeneralResponse response = httpClient.post(image, format, GeneralResponse.class);

		if (response == null || response.images() == null || response.images().isEmpty()) {
			log.error("클로바 OCR 응답에 이미지 결과가 없습니다.");
			throw new BusinessException(ErrorCode.OCR_PROCESSING_FAILED);
		}

		GeneralImage result = response.images().getFirst();
		if (INFER_FAILURE.equals(result.inferResult())) {
			log.info("클로바 OCR 인식 실패. message={}", result.message());
			throw new BusinessException(ErrorCode.OCR_RESULT_EMPTY);
		}
		if (!INFER_SUCCESS.equals(result.inferResult())) {
			log.error("클로바 OCR 처리 오류. inferResult={} message={}", result.inferResult(), result.message());
			throw new BusinessException(ErrorCode.OCR_PROCESSING_FAILED);
		}
		if (result.fields() == null || result.fields().isEmpty()) {
			throw new BusinessException(ErrorCode.OCR_RESULT_EMPTY);
		}

		return ReceiptTextParser.parse(ReceiptLineAssembler.assemble(toFragments(result.fields())));
	}

	private List<ReceiptLineAssembler.Fragment> toFragments(List<GeneralField> fields) {
		return fields.stream()
				.filter(field -> field != null && field.inferText() != null && !field.inferText().isBlank())
				.map(this::toFragment)
				.filter(fragment -> fragment != null)
				.toList();
	}

	private ReceiptLineAssembler.Fragment toFragment(GeneralField field) {
		if (field.boundingPoly() == null || field.boundingPoly().vertices() == null
				|| field.boundingPoly().vertices().isEmpty()) {
			// 좌표가 없으면 어느 줄인지 알 수 없다. 아무 줄에나 끼워 넣으면 총액 줄을 망가뜨린다.
			return null;
		}
		List<Vertex> vertices = field.boundingPoly().vertices();
		double minX = vertices.stream().mapToDouble(Vertex::x).min().orElse(0);
		double minY = vertices.stream().mapToDouble(Vertex::y).min().orElse(0);
		double maxY = vertices.stream().mapToDouble(Vertex::y).max().orElse(0);
		return new ReceiptLineAssembler.Fragment(field.inferText().trim(), minX, (minY + maxY) / 2, maxY - minY);
	}

	private record Vertex(double x, double y) {
	}

	private record BoundingPoly(List<Vertex> vertices) {
	}

	private record GeneralField(String inferText, BoundingPoly boundingPoly) {
	}

	private record GeneralImage(String inferResult, String message, List<GeneralField> fields) {
	}

	private record GeneralResponse(List<GeneralImage> images) {
	}
}
