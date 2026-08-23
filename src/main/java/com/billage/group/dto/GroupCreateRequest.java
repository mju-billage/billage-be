package com.billage.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupCreateRequest(
		@NotBlank(message = "모임 이름은 필수입니다.")
		@Size(max = 10, message = "모임 이름은 10자 이하여야 합니다.")
		String name,

		@Size(max = 30, message = "모임 설명은 30자 이하여야 합니다.")
		String description,

		/** 미리 업로드한 대표 이미지 파일 ID. 본인이 올린 GROUP_IMAGE 파일만 허용된다. 선택값. */
		Long groupImageFileId
) {
}
