package com.billage.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.billage.common.exception.ErrorCode;
import com.billage.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * SecurityFilterChain 단계(GlobalExceptionHandler가 닿지 않는 곳)에서
 * 공통 {@link ErrorResponse} 형식으로 에러 본문을 직접 기록한다.
 */
@Component
public class SecurityErrorResponder {

	private final ObjectMapper objectMapper = new ObjectMapper();

	public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), ErrorResponse.of(errorCode));
	}
}
