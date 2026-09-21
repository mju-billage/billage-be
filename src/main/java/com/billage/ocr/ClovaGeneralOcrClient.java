package com.billage.ocr;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * CLOVA OCR <b>범용</b> 모델 호출. 이 서비스의 영수증 인식은 이 구현으로 확정됐다(2026-09-21).
 *
 * <p>영수증 특화 모델은 승인까지 받았지만 <b>건당 단가 때문에 쓰지 않기로 했다</b>. 그 대신
 * 범용 모델이 돌려주는 글자와 좌표에서 총액·결제일을 우리가 추론한다
 * ({@link ReceiptLineAssembler} 로 좌표에서 줄을 되돌리고 {@link ReceiptTextParser} 가 값을 추려낸다).
 *
 * <p>그래서 <b>인식 품질은 이제 클로바가 아니라 {@link ReceiptTextParser} 의 규칙에 달려 있다.</b>
 * 못 읽는 영수증 서식이 나오면 그 규칙을 늘리는 것이 대응이다.
 *
 * <p>품목({@code items})은 <b>항상 비어 있다</b> — 임시 상태가 아니라 확정된 계약이다.
 * 범용 OCR 로 품목 행만 가려내려면 열 위치까지 추론해야 하는데, 잘못 뽑은 품목은 없는 것보다 나쁘다.
 * 화면이 품목을 필요로 하게 되면 그때 다시 판단한다.
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
		log.info("영수증 인식: CLOVA 범용 OCR 모델. 총액·결제일은 ReceiptTextParser 가 추론하며 품목은 비웁니다.");
	}

	@Override
	public ReceiptOcrResult recognize(byte[] image, ReceiptImageFormat format) {
		GeneralResponse response = httpClient.post(image, format, GeneralResponse.class);

		if (response == null || response.images() == null || response.images().isEmpty()) {
			log.error("클로바 OCR 응답에 이미지 결과가 없습니다.");
			throw new BusinessException(ErrorCode.OCR_PROCESSING_FAILED);
		}

		GeneralImage result = response.images().getFirst();
		if (result == null) {
			// JSON 배열에 null 원소가 들어오면 아래 inferResult() 에서 터진다.
			log.error("클로바 OCR 응답의 이미지 결과가 비어 있습니다.");
			throw new BusinessException(ErrorCode.OCR_PROCESSING_FAILED);
		}
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
