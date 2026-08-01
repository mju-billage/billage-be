package com.billage.auth.social;

import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 소셜 Provider API 호출용 RestClient. 타임아웃을 두지 않으면 Provider 응답 지연 시
 * 로그인 요청 스레드가 무한정 점유될 수 있어 연결·읽기 시간을 제한한다.
 */
final class SocialHttpClient {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	private SocialHttpClient() {
	}

	static RestClient create() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return RestClient.builder().requestFactory(factory).build();
	}
}
