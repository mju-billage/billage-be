package com.billage.membership.dto;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.membership.GroupRole;

import jakarta.validation.constraints.NotBlank;

/**
 * 권한 수정 요청. enum 파싱 실패를 역직렬화 오류가 아니라 {@code INVALID_ROLE} 로 응답하기 위해
 * 문자열로 받아 직접 변환한다.
 */
public record RoleUpdateRequest(
		@NotBlank(message = "변경할 권한은 필수입니다.")
		String role
) {

	public GroupRole toGroupRole() {
		try {
			return GroupRole.valueOf(role.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.INVALID_ROLE);
		}
	}
}
