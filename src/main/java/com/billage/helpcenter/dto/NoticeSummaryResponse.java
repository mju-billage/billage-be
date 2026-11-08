package com.billage.helpcenter.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.helpcenter.Notice;

public record NoticeSummaryResponse(Long noticeId, String title, OffsetDateTime createdAt) {

	public static NoticeSummaryResponse from(Notice notice) {
		return new NoticeSummaryResponse(notice.getId(), notice.getTitle(), KoreanTime.toOffset(notice.getCreatedAt()));
	}
}
