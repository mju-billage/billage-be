package com.billage.group.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.membership.GroupRole;

/**
 * 모임 목록 항목. {@code groupImageUrl} 은 File 도메인 구현 전까지 항상 null.
 */
public record GroupSummaryResponse(
		Long groupId,
		String name,
		String description,
		String groupImageUrl,
		GroupRole myRole,
		long memberCount,
		OffsetDateTime createdAt
) {

	public static GroupSummaryResponse from(GroupListRow row) {
		return new GroupSummaryResponse(row.groupId(), row.name(), row.description(), null,
				row.myRole(), row.memberCount(), KoreanTime.toOffset(row.createdAt()));
	}
}
