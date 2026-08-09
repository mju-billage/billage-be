package com.billage.group.dto;

import com.billage.group.Group;

/**
 * 초대 코드 조회·재발급 응답. 재발급하면 이전 코드는 즉시 무효가 된다.
 */
public record InviteCodeResponse(String inviteCode) {

	public static InviteCodeResponse of(Group group) {
		return new InviteCodeResponse(group.getInviteCode());
	}
}
