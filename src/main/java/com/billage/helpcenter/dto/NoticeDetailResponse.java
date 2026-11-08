package com.billage.helpcenter.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.helpcenter.Notice;

public record NoticeDetailResponse(Long noticeId, String title, String body, OffsetDateTime createdAt) {

	public static NoticeDetailResponse from(Notice notice) {
		return new NoticeDetailResponse(notice.getId(), notice.getTitle(), notice.getBody(),
				KoreanTime.toOffset(notice.getCreatedAt()));
	}
}
