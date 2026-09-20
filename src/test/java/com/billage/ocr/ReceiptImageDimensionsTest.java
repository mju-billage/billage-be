package com.billage.ocr;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

/**
 * 인식 전 이미지 크기 판정 검증.
 *
 * <p>경계값은 짐작이 아니라 <b>실제 CLOVA OCR 에 넣어 확인한 값</b>이다(2026-09-21):
 * 8000x100 은 SUCCESS, 8001x100 과 100x8001 은 ERROR, 9x100 도 ERROR 였다.
 * 즉 8000 은 <b>포함</b>이므로 여기서도 8000 을 통과시킨다 — 한 칸 좁게 막으면 멀쩡한 사진을 거절한다.
 *
 * <p>양쪽이 모두 최대인 8000x8000 은 총 화소가 6400만이어도 ERROR 가 아니라 FAILURE(글자 없음)였다.
 * 가로·세로 각각만 보면 되고 총 화소 제한은 따로 없다.
 */
class ReceiptImageDimensionsTest {

	private byte[] png(int width, int height) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

	@Test
	void 최대_경계값은_통과시킨다() throws IOException {
		assertThatCode(() -> ReceiptImageDimensions.validate(png(8000, 100))).doesNotThrowAnyException();
		assertThatCode(() -> ReceiptImageDimensions.validate(png(100, 8000))).doesNotThrowAnyException();
		// 양쪽 모두 최대여도 클로바는 크기를 문제 삼지 않는다.
		assertThatCode(() -> ReceiptImageDimensions.validate(png(8000, 8000))).doesNotThrowAnyException();
	}

	@Test
	void 최소_경계값은_통과시킨다() throws IOException {
		assertThatCode(() -> ReceiptImageDimensions.validate(png(10, 10))).doesNotThrowAnyException();
	}

	@Test
	void 최대를_한_칸_넘으면_보내기_전에_거절한다() throws IOException {
		assertInvalid(png(8001, 100));
		assertInvalid(png(100, 8001));
	}

	@Test
	void 최소에_한_칸_못_미치면_보내기_전에_거절한다() throws IOException {
		assertInvalid(png(9, 100));
		assertInvalid(png(100, 9));
	}

	/** 형식은 맞는데 내용이 깨진 업로드. 보내 봐야 의미가 없다. */
	@Test
	void 이미지로_읽을_수_없으면_거절한다() {
		assertInvalid("이건 이미지가 아니다".getBytes());
	}

	private void assertInvalid(byte[] image) {
		assertThatThrownBy(() -> ReceiptImageDimensions.validate(image))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_OCR_FILE);
	}
}
