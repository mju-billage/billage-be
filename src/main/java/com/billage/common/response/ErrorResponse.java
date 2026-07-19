package com.billage.common.response;

import java.util.List;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import com.billage.common.exception.ErrorCode;

public record ErrorResponse(
		String code,
		String message,
		List<FieldErrorDetail> fieldErrors
) {

	public record FieldErrorDetail(String field, String reason) {

		private static FieldErrorDetail from(FieldError fieldError) {
			return new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
		}
	}

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode.getCode(), message, List.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
		List<FieldErrorDetail> fieldErrors = bindingResult.getFieldErrors().stream()
				.map(FieldErrorDetail::from)
				.toList();
		return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), fieldErrors);
	}
}
