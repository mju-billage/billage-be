package com.billage.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	INVALID_REQUEST("INVALID_REQUEST", HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	DUPLICATE_REQUEST("DUPLICATE_REQUEST", HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
	INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

	// 인증
	INVALID_CREDENTIALS("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	REFRESH_TOKEN_INVALID("REFRESH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token 입니다."),
	REFRESH_TOKEN_EXPIRED("REFRESH_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "만료된 Refresh Token 입니다."),
	REFRESH_TOKEN_REUSED("REFRESH_TOKEN_REUSED", HttpStatus.UNAUTHORIZED, "이미 사용된 Refresh Token 입니다. 다시 로그인해 주세요."),
	SOCIAL_TOKEN_INVALID("SOCIAL_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "소셜 로그인 토큰이 유효하지 않습니다."),
	TERMS_NOT_AGREED("TERMS_NOT_AGREED", HttpStatus.BAD_REQUEST, "약관 동의가 필요합니다."),

	// 모임
	GROUP_NOT_FOUND("GROUP_NOT_FOUND", HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."),
	NOT_GROUP_MANAGER("NOT_GROUP_MANAGER", HttpStatus.FORBIDDEN, "해당 모임의 관리자가 아닙니다."),
	NOT_GROUP_OWNER("NOT_GROUP_OWNER", HttpStatus.FORBIDDEN, "총무(OWNER) 권한이 필요합니다.");

	private final String code;
	private final HttpStatus status;
	private final String message;
}
