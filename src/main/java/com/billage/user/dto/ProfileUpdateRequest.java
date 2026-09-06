package com.billage.user.dto;

import java.util.Optional;

import jakarta.validation.constraints.Size;

/**
 * 내 정보 수정(부분 수정). 전달되지 않은 필드는 그대로 둔다. 이메일 변경은 MVP 범위 밖이다.
 *
 * <p>{@code profileImageFileId} 는 "미전달(변경 없음)"과 "null 전달(기본 아바타로 되돌리기)"을 구분해야 하므로
 * {@link Optional} 로 받는다 — 모임 대표 이미지와 같은 3-state 규칙이다.
 */
public record ProfileUpdateRequest(
		@Size(min = 1, max = 10, message = "이름은 1~10자여야 합니다.")
		String name,

		Optional<Long> profileImageFileId
) {

	public boolean imageChangeRequested() {
		return profileImageFileId != null;
	}

	/** 새 프로필 이미지 파일 ID. null 이면 기본 아바타로 되돌린다. */
	public Long targetImageFileId() {
		return profileImageFileId == null ? null : profileImageFileId.orElse(null);
	}
}
