package com.billage.folder.dto;

import java.util.Optional;

import jakarta.validation.constraints.Size;

/**
 * 부분 수정. 전달되지 않은 필드는 변경하지 않는다.
 *
 * <p>{@code parentFolderId} 는 "미전달"과 "null 전달(최상위로 이동)"을 구분해야 하므로 {@link Optional} 로 받는다.
 * Jackson 은 필드가 없으면 {@code null}, JSON null 이면 {@code Optional.empty()} 로 바인딩한다.
 *
 * <p>공백 전용 이름(" ")은 {@code @Size(min = 1)} 을 통과하므로 Service 에서 막는다.
 * {@code @NotBlank} 는 null 까지 거부해 부분 수정 규칙을 깨뜨리므로 쓰지 않는다.
 */
public record FolderUpdateRequest(
		@Size(min = 1, max = 20, message = "폴더 이름은 1~20자여야 합니다.")
		String name,

		Optional<Long> parentFolderId
) {

	public boolean moveRequested() {
		return parentFolderId != null;
	}

	/** 이동 대상 상위 폴더 ID. null 이면 최상위로 이동. */
	public Long targetParentId() {
		return parentFolderId == null ? null : parentFolderId.orElse(null);
	}
}
