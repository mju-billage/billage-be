package com.billage.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 보관 제목 변경. 담긴 내용은 바꿀 수 없다. */
public record ArchiveRenameRequest(
		@NotBlank(message = "보관 제목은 필수입니다.")
		@Size(max = 20, message = "보관 제목은 20자 이하여야 합니다.")
		String title
) {
}
