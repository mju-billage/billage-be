package com.billage.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.file.FilePurpose;
import com.billage.file.FileService;
import com.billage.ocr.dto.ReceiptOcrResponse;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 영수증 인식의 접근·입력 규칙 검증. 인식 자체는 테스트 프로필에서 스텁이 처리하므로
 * 여기서는 "누구의 어떤 파일을 인식에 보낼 수 있는가"만 본다.
 */
class ReceiptOcrServiceTest extends IntegrationTest {

	@Autowired
	ReceiptOcrService receiptOcrService;
	@Autowired
	FileService fileService;
	@Autowired
	UserRepository userRepository;

	private Long uploaderId;
	private Long otherUserId;

	@BeforeEach
	void setUp() {
		uploaderId = userRepository.save(User.create("uploader@example.com", "encoded", "올린이")).getId();
		otherUserId = userRepository.save(User.create("other@example.com", "encoded", "남")).getId();
	}

	private Long upload(Long userId, String fileName, String contentType, FilePurpose purpose) {
		MockMultipartFile file = new MockMultipartFile("file", fileName, contentType, "image-bytes".getBytes());
		return fileService.upload(userId, file, purpose).fileId();
	}

	@Test
	void 업로드한_영수증을_인식한다() {
		Long fileId = upload(uploaderId, "receipt.jpg", "image/jpeg", FilePurpose.RECEIPT);

		ReceiptOcrResponse response = receiptOcrService.recognize(fileId, uploaderId);

		assertThat(response.fileId()).isEqualTo(fileId);
		assertThat(response.totalAmount()).isPositive();
		assertThat(response.recognizedAt()).isNotNull();
	}

	/** 남의 영수증에 인식을 돌리면 남의 사진을 훔쳐보는 동시에 건당 비용을 태운다. */
	@Test
	void 남이_올린_파일은_인식할_수_없다() {
		Long fileId = upload(uploaderId, "receipt.jpg", "image/jpeg", FilePurpose.RECEIPT);

		assertThatThrownBy(() -> receiptOcrService.recognize(fileId, otherUserId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 증빙이_아닌_파일은_인식할_수_없다() {
		Long fileId = upload(uploaderId, "profile.jpg", "image/jpeg", FilePurpose.PROFILE_IMAGE);

		assertThatThrownBy(() -> receiptOcrService.recognize(fileId, uploaderId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_OCR_FILE);
	}

	/** 업로드는 webp 를 받지만 클로바 OCR 은 받지 않는다. 보내 보고 실패하지 말고 먼저 막는다. */
	@Test
	void 업로드는_되지만_OCR_이_못_받는_형식은_거절한다() {
		Long fileId = upload(uploaderId, "receipt.webp", "image/webp", FilePurpose.RECEIPT);

		assertThatThrownBy(() -> receiptOcrService.recognize(fileId, uploaderId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_OCR_FILE);
	}

	@Test
	void 없는_파일은_찾을_수_없다() {
		assertThatThrownBy(() -> receiptOcrService.recognize(999_999L, uploaderId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_NOT_FOUND);
	}
}
