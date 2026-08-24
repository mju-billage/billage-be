package com.billage.group.dto;

import java.util.Optional;

import jakarta.validation.constraints.Size;

/**
 * 부분 수정. 전달되지 않은(null) 필드는 변경하지 않는다.
 *
 * <p>{@code groupImageFileId} 는 "미전달(변경 없음)"과 "null 전달(기본 이미지로 되돌리기)"을 구분해야 하므로
 * {@link Optional} 로 받는다. Jackson 은 필드가 없으면 {@code null}, JSON null 이면 {@code Optional.empty()} 로 바인딩한다.
 *
 * <p>공백 전용 이름(" ")은 {@code @Size(min = 1)} 을 통과하므로 Service 에서 막는다.
 * {@code @NotBlank} 는 null 까지 거부해 부분 수정 규칙을 깨뜨리므로 쓰지 않는다.
 */
public record GroupUpdateRequest(
		@Size(min = 1, max = 10, message = "모임 이름은 1~10자여야 합니다.")
		String name,

		@Size(max = 30, message = "모임 설명은 30자 이하여야 합니다.")
		String description,

		Optional<Long> groupImageFileId
) {

	public boolean imageChangeRequested() {
		return groupImageFileId != null;
	}

	/** 새 대표 이미지 파일 ID. null 이면 기본 이미지로 되돌린다. */
	public Long targetImageFileId() {
		return groupImageFileId == null ? null : groupImageFileId.orElse(null);
	}
}
