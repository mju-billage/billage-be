package com.billage.group.dto;

import com.billage.group.Group;
import com.billage.group.ManagerRole;

/**
 * "내 모임" 목록 항목. 목록에서는 초대 코드를 노출하지 않는다.
 */
public record GroupSummaryResponse(
		Long id,
		String name,
		ManagerRole myRole
) {

	public static GroupSummaryResponse of(Group group, ManagerRole myRole) {
		return new GroupSummaryResponse(group.getId(), group.getName(), myRole);
	}
}
