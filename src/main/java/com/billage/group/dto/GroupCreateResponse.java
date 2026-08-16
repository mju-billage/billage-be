package com.billage.group.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.group.GroupSpace;
import com.billage.membership.GroupRole;

public record GroupCreateResponse(
		Long groupId,
		String name,
		String description,
		String groupImageUrl,
		GroupRole myRole,
		OffsetDateTime createdAt
) {

	public static GroupCreateResponse from(GroupSpace group) {
		return new GroupCreateResponse(group.getId(), group.getName(), group.getDescription(), null,
				GroupRole.OWNER, KoreanTime.toOffset(group.getCreatedAt()));
	}
}
