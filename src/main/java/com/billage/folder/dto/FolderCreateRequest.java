package com.billage.folder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FolderCreateRequest(
		@NotBlank(message = "폴더 이름은 필수입니다.")
		@Size(max = 20, message = "폴더 이름은 20자 이하여야 합니다.")
		String name,

		/** null 이면 최상위 폴더로 생성한다. */
		Long parentFolderId
) {
}
