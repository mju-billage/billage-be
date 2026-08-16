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

	// 모임·모임원
	ACCESS_DENIED("ACCESS_DENIED", HttpStatus.FORBIDDEN, "해당 모임에 대한 권한이 없습니다."),
	GROUP_NOT_FOUND("GROUP_NOT_FOUND", HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."),
	MEMBER_NOT_FOUND("MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND, "모임원을 찾을 수 없습니다."),
	MEMBERSHIP_NOT_FOUND("MEMBERSHIP_NOT_FOUND", HttpStatus.NOT_FOUND, "모임 관리자를 찾을 수 없습니다."),
	INVALID_ROLE("INVALID_ROLE", HttpStatus.BAD_REQUEST, "허용되지 않은 권한 값입니다."),
	LAST_OWNER_REQUIRED("LAST_OWNER_REQUIRED", HttpStatus.CONFLICT, "모임에는 최소 1명의 총무가 있어야 합니다."),
	ALREADY_GROUP_MEMBER("ALREADY_GROUP_MEMBER", HttpStatus.CONFLICT, "이미 참여 중인 모임입니다."),
	INVALID_INVITATION_CODE("INVALID_INVITATION_CODE", HttpStatus.BAD_REQUEST, "초대 코드가 올바르지 않습니다."),
	INVITATION_EXPIRED("INVITATION_EXPIRED", HttpStatus.GONE, "만료된 초대 코드입니다."),

	// 폴더·장부
	FOLDER_NOT_FOUND("FOLDER_NOT_FOUND", HttpStatus.NOT_FOUND, "폴더를 찾을 수 없습니다."),
	LEDGER_NOT_FOUND("LEDGER_NOT_FOUND", HttpStatus.NOT_FOUND, "장부를 찾을 수 없습니다."),
	INVALID_PARENT_FOLDER("INVALID_PARENT_FOLDER", HttpStatus.CONFLICT, "상위 폴더로 지정할 수 없는 폴더입니다."),
	GROUP_MISMATCH("GROUP_MISMATCH", HttpStatus.CONFLICT, "다른 모임의 리소스는 사용할 수 없습니다."),
	INVALID_BUDGET("INVALID_BUDGET", HttpStatus.BAD_REQUEST, "예산은 0 이상 999,999,999 이하의 정수여야 합니다.");

	private final String code;
	private final HttpStatus status;
	private final String message;
}
