package com.billage.auth.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로그인 설정값.
 *
 * @param appId 카카오 디벨로퍼스의 앱 ID(숫자). 앱이 보낸 access token 이 우리 앱에서 발급된 것인지 확인한다.
 *              비어 있으면 카카오 로그인은 항상 {@code SOCIAL_TOKEN_INVALID} 다 — 다른 기능의 기동은 막지 않는다.
 */
@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoAuthProperties(Long appId) {
}
