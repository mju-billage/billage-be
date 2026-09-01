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
	METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 요청 방식입니다."),
	UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE,
			"지원하지 않는 Content-Type 입니다."),
	DUPLICATE_REQUEST("DUPLICATE_REQUEST", HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
	INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

	// 인증
	INVALID_CREDENTIALS("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	TOKEN_EXPIRED("TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "만료된 Access Token 입니다."),
	TOKEN_INVALID("TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "유효하지 않은 Access Token 입니다."),
	EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
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
	INVITATION_NOT_FOUND("INVITATION_NOT_FOUND", HttpStatus.NOT_FOUND, "유효한 초대 코드가 없습니다."),

	// 폴더·장부
	FOLDER_NOT_FOUND("FOLDER_NOT_FOUND", HttpStatus.NOT_FOUND, "폴더를 찾을 수 없습니다."),
	LEDGER_NOT_FOUND("LEDGER_NOT_FOUND", HttpStatus.NOT_FOUND, "장부를 찾을 수 없습니다."),
	INVALID_PARENT_FOLDER("INVALID_PARENT_FOLDER", HttpStatus.CONFLICT, "상위 폴더로 지정할 수 없는 폴더입니다."),
	GROUP_MISMATCH("GROUP_MISMATCH", HttpStatus.CONFLICT, "다른 모임의 리소스는 사용할 수 없습니다."),
	INVALID_BUDGET("INVALID_BUDGET", HttpStatus.BAD_REQUEST, "예산은 0 이상 999,999,999 이하의 정수여야 합니다."),

	// 내역
	ENTRY_NOT_FOUND("ENTRY_NOT_FOUND", HttpStatus.NOT_FOUND, "내역을 찾을 수 없습니다."),
	ENTRY_ALREADY_APPROVED("ENTRY_ALREADY_APPROVED", HttpStatus.CONFLICT, "이미 승인된 내역입니다."),
	INVALID_QUERY_PARAMETER("INVALID_QUERY_PARAMETER", HttpStatus.BAD_REQUEST, "조회 조건이 올바르지 않습니다."),

	// 회비
	DUES_NOT_FOUND("DUES_NOT_FOUND", HttpStatus.NOT_FOUND, "회비를 찾을 수 없습니다."),
	DUES_ALREADY_CLOSED("DUES_ALREADY_CLOSED", HttpStatus.CONFLICT, "이미 마감된 회비입니다."),
	DUES_NOT_STARTED("DUES_NOT_STARTED", HttpStatus.CONFLICT, "아직 시작되지 않은 회비입니다."),
	DUES_AMOUNT_IMMUTABLE("DUES_AMOUNT_IMMUTABLE", HttpStatus.BAD_REQUEST, "회비 금액은 수정할 수 없습니다."),
	INVALID_PAYMENT_STATUS("INVALID_PAYMENT_STATUS", HttpStatus.BAD_REQUEST, "허용되지 않은 납부 상태 값입니다."),

	// 보고서
	REPORT_NOT_FOUND("REPORT_NOT_FOUND", HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다."),
	REPORT_RANGE_EMPTY("REPORT_RANGE_EMPTY", HttpStatus.UNPROCESSABLE_ENTITY, "선택한 기간에 보고서로 만들 내역이 없습니다."),

	// 파일
	INVALID_FILE("INVALID_FILE", HttpStatus.BAD_REQUEST, "파일이 비어 있거나 올바르지 않습니다."),
	INVALID_FILE_PURPOSE("INVALID_FILE_PURPOSE", HttpStatus.BAD_REQUEST, "허용되지 않은 파일 용도입니다."),
	FILE_NOT_FOUND("FILE_NOT_FOUND", HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
	UNSUPPORTED_FILE_TYPE("UNSUPPORTED_FILE_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 파일 형식입니다."),
	FILE_SIZE_EXCEEDED("FILE_SIZE_EXCEEDED", HttpStatus.PAYLOAD_TOO_LARGE, "파일 용량이 허용 범위를 초과했습니다."),
	FILE_IN_USE("FILE_IN_USE", HttpStatus.CONFLICT, "다른 곳에 연결된 파일은 삭제할 수 없습니다."),
	FILE_UPLOAD_FAILED("FILE_UPLOAD_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다."),
	FILE_DELETE_FAILED("FILE_DELETE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "파일 삭제에 실패했습니다.");

	private final String code;
	private final HttpStatus status;
	private final String message;
}
