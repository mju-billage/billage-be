package com.billage.file;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import lombok.RequiredArgsConstructor;

/**
 * 응답에 담을 파일 URL을 만든다.
 *
 * <p>예전에는 {@code /api/v1/files/{id}/content} 상대경로를 그대로 내려줬는데, RN 앱의 {@code <Image>} 는
 * 상대경로를 해석하지 못해 프론트가 매번 base URL을 조합해야 했다. 조합 규칙이 화면마다 어긋나기 쉬워
 * 서버가 절대 URL로 완성해 내려준다.
 *
 * <p>주소를 정하는 순서:
 * <ol>
 *   <li>{@code billage.file.public-base-url} 설정값 — 리버스 프록시 뒤라 요청만으로는 외부 주소를 알 수 없는
 *       환경에서 확실하게 고정하는 수단이다.</li>
 *   <li>현재 요청에서 유추 — {@code server.forward-headers-strategy=framework} 덕분에 {@code X-Forwarded-*} 를
 *       반영한 실제 외부 주소가 나온다.</li>
 *   <li>요청 컨텍스트가 없으면(배치·테스트 등) 기존처럼 상대경로.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class FileUrlResolver {

	private static final String CONTENT_PATH_FORMAT = "/api/v1/files/%d/content";

	private final FileProperties properties;

	public String resolve(Long fileId) {
		String path = CONTENT_PATH_FORMAT.formatted(fileId);
		String baseUrl = baseUrl();
		return baseUrl.isEmpty() ? path : baseUrl + path;
	}

	private String baseUrl() {
		String configured = properties.publicBaseUrl();
		if (configured != null && !configured.isBlank()) {
			return trimTrailingSlash(configured.trim());
		}
		if (RequestContextHolder.getRequestAttributes() == null) {
			return "";
		}
		return trimTrailingSlash(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
	}

	private String trimTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
