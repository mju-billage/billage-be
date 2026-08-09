package com.billage.group.dto;

import java.time.LocalDateTime;

import com.billage.group.GroupManager;
import com.billage.group.ManagerRole;
import com.billage.user.User;

/**
 * 모임 관리자(권한 보유자) 목록 항목. 모임원 명단(GroupMember)이 아니라 권한 주체 목록이다.
 */
public record GroupManagerResponse(
		Long userId,
		String name,
		String email,
		ManagerRole role,
		LocalDateTime joinedAt
) {

	public static GroupManagerResponse of(GroupManager manager, User user) {
		return new GroupManagerResponse(manager.getUserId(), user.getName(), user.getEmail(),
				manager.getRole(), manager.getCreatedAt());
	}
}
