package com.billage.group.dto;

import java.time.LocalDateTime;

import com.billage.group.Group;
import com.billage.group.GroupStatus;
import com.billage.group.ManagerRole;

/**
 * 모임 상세 응답. 요청자의 권한({@code myRole})을 함께 내려 클라이언트가 UI 제한을 판단하게 한다.
 */
public record GroupResponse(
		Long id,
		String name,
		String inviteCode,
		GroupStatus status,
		ManagerRole myRole,
		LocalDateTime createdAt
) {

	public static GroupResponse of(Group group, ManagerRole myRole) {
		return new GroupResponse(group.getId(), group.getName(), group.getInviteCode(),
				group.getStatus(), myRole, group.getCreatedAt());
	}
}
