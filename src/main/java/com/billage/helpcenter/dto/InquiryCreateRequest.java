package com.billage.helpcenter.dto;

import com.billage.helpcenter.Inquiry;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 문의 접수 요청. 길이 상한은 화면명세에 값이 없어 제목 50자·내용 1000자로 정했다(2026-11-08). */
public record InquiryCreateRequest(
		@NotBlank(message = "이메일은 필수입니다.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String email,

		@NotBlank(message = "문의 제목은 필수입니다.")
		@Size(max = Inquiry.MAX_TITLE_LENGTH, message = "문의 제목은 50자 이하여야 합니다.")
		String title,

		@NotBlank(message = "문의 내용은 필수입니다.")
		@Size(max = Inquiry.MAX_CONTENT_LENGTH, message = "문의 내용은 1000자 이하여야 합니다.")
		String content
) {
}
