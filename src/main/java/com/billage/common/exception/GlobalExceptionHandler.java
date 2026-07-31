package com.billage.common.exception;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.billage.common.response.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode, e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
		ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode, e.getBindingResult()));
	}

	@ExceptionHandler(PessimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> handleLockFailure(PessimisticLockingFailureException e) {
		// 동일 토큰 동시 재발급 등으로 락 획득에 실패(타임아웃)한 경우 → 중복 요청으로 응답
		ErrorCode errorCode = ErrorCode.DUPLICATE_REQUEST;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
		log.error("Unexpected error", e);
		ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode));
	}
}
