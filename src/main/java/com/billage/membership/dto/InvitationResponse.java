package com.billage.membership.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.membership.GroupInvitation;

public record InvitationResponse(
		String invitationCode,
		String invitationLink,
		OffsetDateTime expiresAt
) {

	public static InvitationResponse from(GroupInvitation invitation) {
		return new InvitationResponse(
				invitation.getCode(),
				"billage://groups/join?code=" + invitation.getCode(),
				KoreanTime.toOffset(invitation.getExpiresAt()));
	}
}
