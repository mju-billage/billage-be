package com.billage.ocr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 인식에 보내기 전 이미지 크기를 확인한다.
 *
 * <p>클로바는 가로·세로가 범위를 벗어나면 <b>HTTP 200 에 {@code inferResult: ERROR}</b> 로 답한다
 * (실제 응답: "The image resolution limit has been exceeded... between 10 px and 8000 px").
 * 그런데 {@code ERROR} 는 클로바 쪽 장애에도 쓰여서, 응답만 보고는 "사진을 바꿔 찍으면 되는 일"과
 * "서버 장애"를 가를 수 없다 — 메시지 문자열로 가르는 것은 깨지기 쉽다.
 *
 * <p>그래서 보내기 전에 우리가 판정한다. 범위를 벗어난 사진은 {@code INVALID_OCR_FILE(400)} 로 돌려주어
 * 사용자가 "다시 찍으세요"를 볼 수 있게 하고, 응답의 {@code ERROR} 는 장애로만 남긴다.
 * 덤으로 어차피 실패할 요청에 건당 과금을 쓰지 않는다.
 *
 * <p>헤더만 읽으므로 이미지를 통째로 디코딩하지 않는다.
 */
@Slf4j
final class ReceiptImageDimensions {

	/**
	 * 클로바 OCR 이 받는 가로·세로 범위. <b>양 끝을 포함</b>한다 — 실제로 넣어 확인했다(2026-09-21):
	 * 8000x100 은 SUCCESS, 8001x100 과 9x100 은 ERROR 였다. 한 칸 좁게 막으면 멀쩡한 사진을 거절하게 된다.
	 *
	 * <p>가로·세로 각각만 보면 된다. 양쪽이 모두 8000 인 이미지(6400만 화소)도 크기 문제로는 거부되지 않았다.
	 */
	private static final int MIN_PIXELS = 10;
	private static final int MAX_PIXELS = 8000;

	private ReceiptImageDimensions() {
	}

	static void validate(byte[] image) {
		try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (!readers.hasNext()) {
				// jpg·png 만 여기까지 오므로, 못 읽으면 내용이 깨진 파일이다.
				throw new BusinessException(ErrorCode.INVALID_OCR_FILE);
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(stream);
				checkBounds(reader.getWidth(0), reader.getHeight(0));
			} finally {
				reader.dispose();
			}
		} catch (IOException e) {
			log.info("영수증 이미지 크기를 읽지 못했습니다.", e);
			throw new BusinessException(ErrorCode.INVALID_OCR_FILE);
		}
	}

	private static void checkBounds(int width, int height) {
		int shorterSide = Math.min(width, height);
		int longerSide = Math.max(width, height);
		if (shorterSide < MIN_PIXELS || longerSide > MAX_PIXELS) {
			throw new BusinessException(ErrorCode.INVALID_OCR_FILE,
					"인식할 수 있는 이미지 크기를 벗어났습니다(가로·세로 %d~%dpx). 현재 %dx%d."
							.formatted(MIN_PIXELS, MAX_PIXELS, width, height));
		}
	}
}
