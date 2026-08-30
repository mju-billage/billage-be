package com.billage.auth.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.billage.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 인증되지 않은 요청에 대해 공통 에러 형식으로 401을 응답한다.
 *
 * <p>클라이언트가 "토큰을 갱신하면 되는 상황"과 "다시 로그인해야 하는 상황"을 구분할 수 있도록 사유를 나눈다.
 * <ul>
 *   <li>{@code TOKEN_EXPIRED} — Access Token 만료. 재발급 후 재시도하면 된다.</li>
 *   <li>{@code TOKEN_INVALID} — 서명 불일치·형식 오류 등 손상된 토큰. 재발급해도 소용없다.</li>
 *   <li>{@code UNAUTHORIZED} — 토큰 자체가 없음.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final SecurityErrorResponder responder;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		responder.write(response, errorCodeOf(authException));
	}

	private ErrorCode errorCodeOf(AuthenticationException authException) {
		if (!(authException instanceof OAuth2AuthenticationException oauth2Exception)) {
			// 토큰이 아예 없으면 리소스 서버 필터가 인증을 시도조차 하지 않는다.
			return ErrorCode.UNAUTHORIZED;
		}

		String errorCode = oauth2Exception.getError().getErrorCode();
		if (BearerTokenErrorCodes.INVALID_TOKEN.equals(errorCode)) {
			// Nimbus 는 만료를 별도 에러 코드로 알려주지 않는다 — 설명 문구로만 구분할 수 있다
			// ("An error occurred while attempting to decode the Jwt: Jwt expired at ...").
			// 문구에 기대는 판정이라 JwtAuthenticationEntryPointTest 로 회귀를 막는다.
			String description = oauth2Exception.getError().getDescription();
			return description != null && description.toLowerCase().contains("expired")
					? ErrorCode.TOKEN_EXPIRED
					: ErrorCode.TOKEN_INVALID;
		}
		return ErrorCode.UNAUTHORIZED;
	}
}
