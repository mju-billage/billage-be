package com.billage.membership.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.membership.GroupMembership;
import com.billage.membership.GroupRole;

public record MembershipResponse(
		Long membershipId,
		Long userId,
		String name,
		GroupRole role,
		OffsetDateTime joinedAt
) {

	public static MembershipResponse of(GroupMembership membership, String name) {
		return new MembershipResponse(membership.getId(), membership.getUserId(), name,
				membership.getRole(), KoreanTime.toOffset(membership.getJoinedAt()));
	}
}
