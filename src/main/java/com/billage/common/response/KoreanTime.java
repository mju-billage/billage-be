package com.billage.common.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 응답용 시각 변환. API 규칙상 날짜·시간은 ISO 8601 + 오프셋(`2026-07-20T18:00:00+09:00`)으로 내보낸다.
 * DB에는 Asia/Seoul 기준 {@link LocalDateTime}으로 저장한다.
 */
public final class KoreanTime {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private KoreanTime() {
	}

	public static OffsetDateTime toOffset(LocalDateTime dateTime) {
		return dateTime == null ? null : dateTime.atZone(ZONE).toOffsetDateTime();
	}
}
