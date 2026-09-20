package com.billage.ocr;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 사용자당 영수증 인식 횟수 제한.
 *
 * <p>외부 OCR 은 건당 과금이라 상한이 없으면 앱의 재시도 버그나 악의적 반복 한 번으로 비용이 샌다.
 * 업로드 자체는 제한하지 않는다 — 돈이 드는 쪽은 인식이다.
 *
 * <p>서버 메모리에 둔다. 단일 EC2 한 대로 운영하므로(그리고 재시작으로 카운트가 풀려도 손해가 아니라
 * 잠깐 느슨해질 뿐이므로) 저장소를 따로 두지 않는다. 서버를 여러 대로 늘리면 이 판정은 대수만큼 느슨해진다.
 */
@Component
@RequiredArgsConstructor
public class ReceiptOcrRateLimiter {

	private static final Duration WINDOW = Duration.ofHours(1);

	/**
	 * 이 수를 넘으면 들어온 김에 만료된 사용자를 걷어낸다. 스캔하고 돌아오지 않는 사용자의 항목은
	 * 스스로 사라지지 않는데, 이것만 위해 프로젝트에 없는 스케줄러를 들이기보다 호출 시점에 처리한다.
	 * 정리 비용은 맵 크기에 비례하므로 매번 돌리지 않는다.
	 */
	private static final int EVICT_THRESHOLD = 1_000;

	private final OcrProperties properties;

	private final Map<Long, Deque<Instant>> recentScans = new ConcurrentHashMap<>();

	/** 한 번의 인식을 기록한다. 상한을 넘으면 기록하지 않고 거절한다. */
	public void check(Long userId) {
		if (recentScans.size() > EVICT_THRESHOLD) {
			evictExpired();
		}

		Instant now = Instant.now();
		Instant windowStart = now.minus(WINDOW);

		// compute 로 키 단위 원자성을 얻는다. 같은 사용자가 두 기기에서 동시에 눌러도 한 번씩만 센다.
		recentScans.compute(userId, (key, timestamps) -> {
			Deque<Instant> window = (timestamps == null) ? new ArrayDeque<>() : timestamps;
			dropExpired(window, windowStart);
			if (window.size() >= properties.maxScansPerHour()) {
				throw new BusinessException(ErrorCode.OCR_RATE_LIMITED);
			}
			window.addLast(now);
			return window;
		});
	}

	/** 창이 비워진 사용자를 맵에서 걷어낸다. */
	void evictExpired() {
		Instant windowStart = Instant.now().minus(WINDOW);
		for (Long userId : recentScans.keySet()) {
			recentScans.computeIfPresent(userId, (key, window) -> {
				dropExpired(window, windowStart);
				return window.isEmpty() ? null : window;
			});
		}
	}

	/** 현재 창에 기록된 횟수. 테스트에서 "거절된 요청은 세지 않는다"를 확인하는 데 쓴다. */
	int recordedCount(Long userId) {
		Deque<Instant> window = recentScans.get(userId);
		return window == null ? 0 : window.size();
	}

	private void dropExpired(Deque<Instant> window, Instant windowStart) {
		while (!window.isEmpty() && window.peekFirst().isBefore(windowStart)) {
			window.pollFirst();
		}
	}
}
