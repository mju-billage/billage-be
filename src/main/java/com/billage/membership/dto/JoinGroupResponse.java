package com.billage.membership.dto;

import com.billage.membership.GroupMembership;
import com.billage.membership.GroupRole;

public record JoinGroupResponse(
		Long groupId,
		Long membershipId,
		GroupRole role
) {

	public static JoinGroupResponse from(GroupMembership membership) {
		return new JoinGroupResponse(membership.getGroup().getId(), membership.getId(), membership.getRole());
	}
}
