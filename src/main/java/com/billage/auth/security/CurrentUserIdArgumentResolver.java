package com.billage.auth.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@link CurrentUserId} 파라미터에 JWT subject(사용자 ID)를 주입한다.
 * 인증 필터를 통과한 요청에서만 호출되지만, 방어적으로 principal 이 없으면 401 로 처리한다.
 *
 * <p>계정이 아직 있는지도 확인한다. Access Token 은 서버에 저장하지 않아 탈퇴해도 만료(30분)까지
 * 서명이 유효한데, 그 토큰으로 들어온 요청은 사용자를 참조하는 순간 FK 제약에 걸려 500 이 된다.
 * 파일 업로드는 그 전에 저장소에 바이트를 이미 써 두므로, 메타데이터 없는 객체가 저장소에 남는다.
 * 여기서 401 로 끊어 그런 요청이 아무것도 만들지 못하게 한다.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

	private final UserRepository userRepository;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentUserId.class)
				&& Long.class.isAssignableFrom(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		Long userId = Long.valueOf(jwt.getSubject());
		if (!userRepository.existsById(userId)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "탈퇴했거나 존재하지 않는 계정입니다.");
		}
		return userId;
	}
}
