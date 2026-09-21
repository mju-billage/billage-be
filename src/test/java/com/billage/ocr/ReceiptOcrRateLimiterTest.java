package com.billage.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

/**
 * 인식 횟수 제한 검증. 건당 과금이라 이 판정이 헐거우면 바로 비용이 된다.
 */
class ReceiptOcrRateLimiterTest {

	private ReceiptOcrRateLimiter limiter(int maxScansPerHour) {
		return new ReceiptOcrRateLimiter(new OcrProperties(OcrProperties.ProviderType.STUB, "", "",
				DataSize.ofMegabytes(4), Duration.ofSeconds(3), Duration.ofSeconds(10), maxScansPerHour));
	}

	@Test
	void 상한까지는_허용하고_넘으면_거절한다() {
		ReceiptOcrRateLimiter limiter = limiter(3);

		assertThatCode(() -> {
			limiter.check(1L);
			limiter.check(1L);
			limiter.check(1L);
		}).doesNotThrowAnyException();

		assertThatThrownBy(() -> limiter.check(1L))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.OCR_RATE_LIMITED);
	}

	/** 한 사용자가 상한을 채웠다고 다른 사용자까지 막히면 안 된다. */
	@Test
	void 제한은_사용자별로_따로_센다() {
		ReceiptOcrRateLimiter limiter = limiter(1);
		limiter.check(1L);

		assertThatCode(() -> limiter.check(2L)).doesNotThrowAnyException();
		assertThatThrownBy(() -> limiter.check(1L)).isInstanceOf(BusinessException.class);
	}

	/** 거절된 요청까지 세면 창이 끝날 때까지 상한이 영영 풀리지 않는다. */
	@Test
	void 거절된_요청은_횟수에_포함하지_않는다() {
		ReceiptOcrRateLimiter limiter = limiter(1);
		limiter.check(1L);

		assertThatThrownBy(() -> limiter.check(1L)).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> limiter.check(1L)).isInstanceOf(BusinessException.class);

		assertThat(limiter.recordedCount(1L)).isEqualTo(1);
	}

	@Test
	void 만료_정리는_아직_유효한_기록을_지우지_않는다() {
		ReceiptOcrRateLimiter limiter = limiter(5);
		limiter.check(1L);

		limiter.evictExpired();

		assertThat(limiter.recordedCount(1L)).isEqualTo(1);
	}
}
