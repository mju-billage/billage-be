package com.billage.membership.dto;

import com.billage.membership.GroupMembership;
import com.billage.membership.GroupRole;

public record RoleUpdateResponse(
		Long membershipId,
		Long userId,
		String name,
		GroupRole role
) {

	public static RoleUpdateResponse of(GroupMembership membership, String name) {
		return new RoleUpdateResponse(membership.getId(), membership.getUserId(), name, membership.getRole());
	}
}
