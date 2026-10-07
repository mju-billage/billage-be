package com.billage.ocr;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.file.FilePurpose;
import com.billage.file.FileService;
import com.billage.file.UploadedFile;
import com.billage.ocr.dto.ReceiptOcrResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 업로드된 영수증 이미지에서 상호·결제일·총액·품목을 읽는다.
 *
 * <p>업로드와 인식을 나눈 것은 명세({@code POST /api/v1/files/{fileId}/ocr})가 그렇게 정했기 때문이고,
 * 그 편이 실제로도 맞다 — 스캔한 영수증은 어차피 증빙으로 내역에 붙으므로 같은 이미지를 두 번 올릴 이유가 없고,
 * 인식에 실패해도 파일이 남아 있어 사용자가 "그냥 증빙으로만 첨부"할 수 있다.
 *
 * <p>접근 권한은 {@link FileService#getAccessibleFile} 이 판정한다. 아직 내역에 붙지 않은 업로드는
 * 업로더 본인만 열 수 있으므로, 남이 올린 영수증에 인식을 돌려 비용을 태우는 것도 같이 막힌다.
 *
 * <p><b>이 클래스에 트랜잭션을 걸지 않는다.</b> 인식은 수 초가 걸리는 외부 호출인데, 트랜잭션 안에서
 * 부르면 그동안 DB 커넥션을 붙잡는다 — 커넥션 풀이 작은 서버라 동시 인식 몇 건으로 풀이 마른다.
 * DB 를 건드리는 구간은 {@link FileService} 의 짧은 읽기 트랜잭션으로 이미 끝나 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptOcrService {

	private final FileService fileService;
	private final ReceiptOcrClient ocrClient;
	private final ReceiptOcrRateLimiter rateLimiter;
	private final OcrProperties properties;
	private final ReceiptOcrStore store;

	public ReceiptOcrResponse recognize(Long fileId, Long userId) {
		UploadedFile file = fileService.getAccessibleFile(fileId, userId);
		if (file.getPurpose() != FilePurpose.RECEIPT) {
			throw new BusinessException(ErrorCode.INVALID_OCR_FILE);
		}
		// 업로드가 허용하는 형식이라고 OCR 이 받는 것은 아니다(webp). 바이트를 읽기 전에 걸러낸다.
		ReceiptImageFormat format = ReceiptImageFormat.from(file.getContentType());
		if (file.getSize() > properties.maxImageSize().toBytes()) {
			throw new BusinessException(ErrorCode.INVALID_OCR_FILE,
					"인식할 수 있는 이미지 크기를 초과했습니다(최대 " + properties.maxImageSize().toMegabytes() + "MB).");
		}

		byte[] image = readBytes(file);
		// 크기는 바이트를 손에 넣은 뒤에야 알 수 있다. 여기서 걸러 내면 어차피 실패할 요청에
		// 건당 과금을 쓰지 않고, 사용자도 502 대신 "다시 찍으세요"를 본다.
		ReceiptImageDimensions.validate(image);

		// 돈이 나가는 호출 바로 앞에서 센다. 앞에서 세면 깨진 이미지나 해상도 초과처럼
		// 애초에 클로바를 부르지도 않는 요청이 사용자의 시간당 한도를 갉아먹는다.
		rateLimiter.check(userId);

		ReceiptOcrResult result = ocrClient.recognize(image, format);
		return keep(fileId, result, LocalDateTime.now());
	}

	/**
	 * 인식 결과를 보관한다. 이 파일이 내역의 증빙으로 붙으면 내역 상세에서 다시 읽힌다.
	 *
	 * <p>보관에 실패해도 인식 결과는 돌려준다 — 이미 과금된 호출이고, 사용자는 지금 이 값으로 폼을 채워야 한다.
	 * 실패는 드물다: 같은 파일을 동시에 두 번 인식해 UNIQUE 에 걸리거나, 그 사이 파일이 지워진 경우다.
	 * 앞의 경우는 한 번 더 하면 상대가 만든 행을 덮어쓴다.
	 */
	private ReceiptOcrResponse keep(Long fileId, ReceiptOcrResult result, LocalDateTime recognizedAt) {
		try {
			return store.save(fileId, result, recognizedAt);
		} catch (DataIntegrityViolationException first) {
			try {
				return store.save(fileId, result, recognizedAt);
			} catch (DataIntegrityViolationException second) {
				log.warn("영수증 인식 결과 보관 실패. fileId={} reason={}", fileId, second.getMessage());
				return ReceiptOcrResponse.of(fileId, result, recognizedAt);
			}
		}
	}

	/**
	 * 이 파일들 중 가장 최근에 인식한 결과. 내역 상세가 쓴다 — OCR 을 다시 부르지 않고 보관된 값을 읽는다.
	 */
	public Optional<ReceiptOcrResponse> findLatestOf(Collection<Long> fileIds) {
		return store.findLatestOf(fileIds);
	}

	/**
	 * 이미지를 통째로 메모리에 올린다. OCR 요청이 Base64 본문 하나라 스트리밍할 방법이 없다 —
	 * 그래서 {@code billage.ocr.max-image-size} 로 크기를 먼저 막아 둔다.
	 */
	private byte[] readBytes(UploadedFile file) {
		Resource content = fileService.loadContent(file);
		try {
			return content.getContentAsByteArray();
		} catch (IOException e) {
			log.error("영수증 이미지 읽기 실패. storageKey={}", file.getStorageKey(), e);
			throw new BusinessException(ErrorCode.OCR_PROCESSING_FAILED);
		}
	}
}
