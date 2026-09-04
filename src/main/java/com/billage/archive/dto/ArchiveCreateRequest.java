package com.billage.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 보관 실행 요청. 화면은 제목 하나만 받는다 — 무엇을 담을지는 서버가 정한다(모임의 현재 장부 전부). */
public record ArchiveCreateRequest(
		@NotBlank(message = "보관 제목은 필수입니다.")
		@Size(max = 20, message = "보관 제목은 20자 이하여야 합니다.")
		String title
) {
}
