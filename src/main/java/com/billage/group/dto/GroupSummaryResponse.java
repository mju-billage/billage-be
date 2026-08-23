package com.billage.group.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.membership.GroupRole;

/** 모임 목록 항목. */
public record GroupSummaryResponse(
		Long groupId,
		String name,
		String description,
		String groupImageUrl,
		GroupRole myRole,
		long memberCount,
		OffsetDateTime createdAt
) {

	public static GroupSummaryResponse of(GroupListRow row, String groupImageUrl) {
		return new GroupSummaryResponse(row.groupId(), row.name(), row.description(), groupImageUrl,
				row.myRole(), row.memberCount(), KoreanTime.toOffset(row.createdAt()));
	}
}
