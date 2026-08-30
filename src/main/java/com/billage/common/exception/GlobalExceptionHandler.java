package com.billage.common.exception;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

	/** 본문·파라미터 파싱 실패(JSON 형식 오류, enum·타입 변환 실패 등)는 내부 오류가 아니라 요청 오류다. */
	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ErrorResponse> handleUnreadableRequest(Exception e) {
		ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode));
	}

	/**
	 * 필수 쿼리파라미터·멀티파트 파트 누락.
	 *
	 * <p>아래 프레임워크 예외들은 원래 {@code DefaultHandlerExceptionResolver}(order 2)가 4xx 로 바꿔주지만,
	 * 이 advice 의 {@code Exception} catch-all 을 들고 있는 {@code ExceptionHandlerExceptionResolver}(order 0)가
	 * 먼저 돌아 전부 500 으로 나가버린다. 그래서 구체 타입으로 다시 잡아 상태코드를 되돌린다.
	 */
	@ExceptionHandler({ MissingServletRequestParameterException.class, MissingServletRequestPartException.class })
	public ResponseEntity<ErrorResponse> handleMissingRequestValue(Exception e) {
		ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode));
	}

	/** 매핑되지 않은 경로. */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
		ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode));
	}

	/** 경로는 있으나 허용되지 않은 HTTP 메서드. */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode));
	}

	/** Content-Type 누락·오기. 요청 본문 자체를 읽지 못한 경우다. */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
		ErrorCode errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode));
	}

	/**
	 * 멀티파트 컨테이너 상한({@code spring.servlet.multipart.max-file-size}) 초과.
	 *
	 * <p>정책 상한({@code billage.file.max-size})은 {@code FileService} 가 먼저 걸러내지만, 컨테이너 상한을 넘으면
	 * 요청이 Service 에 닿기 전에 여기서 끝난다. 두 경로가 같은 코드로 응답해야 클라이언트가 분기를 하나만 두면 된다.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
		ErrorCode errorCode = ErrorCode.FILE_SIZE_EXCEEDED;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode));
	}

	/** 존재하지 않는 정렬 필드 등 조회 조건 오류. */
	@ExceptionHandler(PropertyReferenceException.class)
	public ResponseEntity<ErrorResponse> handleInvalidQueryParameter(PropertyReferenceException e) {
		ErrorCode errorCode = ErrorCode.INVALID_QUERY_PARAMETER;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode));
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
