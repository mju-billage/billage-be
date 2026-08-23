package com.billage.group.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.group.GroupSpace;
import com.billage.membership.GroupRole;

public record GroupDetailResponse(
		Long groupId,
		String name,
		String description,
		String groupImageUrl,
		GroupRole myRole,
		long memberCount,
		long ownerCount,
		OffsetDateTime createdAt
) {

	public static GroupDetailResponse of(GroupSpace group, String groupImageUrl, GroupRole myRole,
			long memberCount, long ownerCount) {
		return new GroupDetailResponse(group.getId(), group.getName(), group.getDescription(), groupImageUrl,
				myRole, memberCount, ownerCount, KoreanTime.toOffset(group.getCreatedAt()));
	}
}
