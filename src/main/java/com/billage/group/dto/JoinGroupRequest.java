package com.billage.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 초대 코드로 모임에 참여하는 요청. 코드는 8자리이며, 대소문자·공백은 서버에서 정규화한다.
 */
public record JoinGroupRequest(
		@NotBlank @Size(max = 16) String inviteCode
) {
}
