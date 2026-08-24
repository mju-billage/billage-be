package com.billage.membership.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.membership.GroupMembership;
import com.billage.membership.GroupRole;
import com.billage.user.User;

/**
 * 모임 관리자 항목. 관리자 프로필 화면이 계정 식별용으로 이메일을 함께 보여 준다.
 * 탈퇴 등으로 사용자를 찾지 못하면 이름·이메일은 null 로 둔다(목록 자체는 깨지지 않아야 한다).
 */
public record MembershipResponse(
		Long membershipId,
		Long userId,
		String name,
		String email,
		GroupRole role,
		OffsetDateTime joinedAt
) {

	public static MembershipResponse of(GroupMembership membership, User user) {
		return new MembershipResponse(membership.getId(), membership.getUserId(),
				user == null ? null : user.getName(),
				user == null ? null : user.getEmail(),
				membership.getRole(), KoreanTime.toOffset(membership.getJoinedAt()));
	}
}
