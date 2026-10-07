package com.billage.ocr;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptOcrRepository extends JpaRepository<ReceiptOcr, Long> {

	Optional<ReceiptOcr> findByFileId(Long fileId);

	/** 여러 파일 중 가장 최근에 인식한 결과. 내역에 증빙이 여러 장일 때 어느 것을 보여 줄지 정한다. */
	Optional<ReceiptOcr> findFirstByFileIdInOrderByRecognizedAtDescIdDesc(Collection<Long> fileIds);
}
