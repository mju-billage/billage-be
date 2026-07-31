package com.billage.auth.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 구글 로그인 설정값.
 *
 * @param clientId ID Token의 aud(Web/Server 클라이언트 ID) 검증에 사용
 */
@ConfigurationProperties(prefix = "oauth.google")
public record SocialAuthProperties(String clientId) {
}
