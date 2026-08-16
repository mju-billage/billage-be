package com.billage.group.dto;

import com.billage.group.GroupSpace;

public record GroupUpdateResponse(
		Long groupId,
		String name,
		String description,
		String groupImageUrl
) {

	public static GroupUpdateResponse from(GroupSpace group) {
		return new GroupUpdateResponse(group.getId(), group.getName(), group.getDescription(), null);
	}
}
