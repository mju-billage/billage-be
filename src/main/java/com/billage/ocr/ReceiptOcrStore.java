package com.billage.ocr;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.billage.ocr.dto.ReceiptOcrResponse;

import lombok.RequiredArgsConstructor;

/**
 * 인식 결과 보관. 인식 자체({@link ReceiptOcrService})는 외부 호출이라 트랜잭션 밖에서 돌고,
 * 저장과 조회만 여기서 짧게 트랜잭션을 연다.
 */
@Component
@RequiredArgsConstructor
class ReceiptOcrStore {

	private final ReceiptOcrRepository repository;

	/** 파일당 한 건. 이미 있으면 덮어쓴다. */
	@Transactional
	public ReceiptOcrResponse save(Long fileId, ReceiptOcrResult result, LocalDateTime recognizedAt) {
		ReceiptOcr ocr = repository.findByFileId(fileId)
				.map(existing -> {
					existing.overwrite(result, recognizedAt);
					return existing;
				})
				.orElseGet(() -> ReceiptOcr.of(fileId, result, recognizedAt));
		return toResponse(repository.saveAndFlush(ocr));
	}

	@Transactional(readOnly = true)
	public Optional<ReceiptOcrResponse> findLatestOf(Collection<Long> fileIds) {
		if (fileIds.isEmpty()) {
			return Optional.empty();
		}
		return repository.findFirstByFileIdInOrderByRecognizedAtDescIdDesc(fileIds).map(this::toResponse);
	}

	private ReceiptOcrResponse toResponse(ReceiptOcr ocr) {
		return ReceiptOcrResponse.of(ocr.getFileId(), ocr.toResult(), ocr.getRecognizedAt());
	}
}
