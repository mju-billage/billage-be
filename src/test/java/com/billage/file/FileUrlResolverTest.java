package com.billage.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 응답 파일 URL 이 절대 주소로 나가는지 확인한다.
 * 상대경로로 돌아가면 RN {@code <Image>} 가 이미지를 못 띄우는데, 서버 테스트로는 드러나지 않는 종류의 회귀다.
 */
class FileUrlResolverTest {

	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	@DisplayName("설정값이 있으면 그 주소를 붙인다")
	void usesConfiguredBaseUrl() {
		FileUrlResolver resolver = new FileUrlResolver(propertiesWithBaseUrl("https://api.billage.app"));

		assertThat(resolver.resolve(7L)).isEqualTo("https://api.billage.app/api/v1/files/7/content");
	}

	@Test
	@DisplayName("설정값 끝의 슬래시는 중복되지 않게 정리한다")
	void trimsTrailingSlash() {
		FileUrlResolver resolver = new FileUrlResolver(propertiesWithBaseUrl("https://api.billage.app/"));

		assertThat(resolver.resolve(7L)).isEqualTo("https://api.billage.app/api/v1/files/7/content");
	}

	@Test
	@DisplayName("설정값이 비어 있으면 현재 요청에서 주소를 유추한다")
	void fallsBackToCurrentRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setScheme("https");
		request.setServerName("52-78-148-114.nip.io");
		request.setServerPort(443);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		FileUrlResolver resolver = new FileUrlResolver(propertiesWithBaseUrl(""));

		assertThat(resolver.resolve(7L))
				.isEqualTo("https://52-78-148-114.nip.io/api/v1/files/7/content");
	}

	@Test
	@DisplayName("요청 컨텍스트가 없으면 상대경로로 물러난다")
	void fallsBackToRelativePath() {
		FileUrlResolver resolver = new FileUrlResolver(propertiesWithBaseUrl(""));

		assertThat(resolver.resolve(7L)).isEqualTo("/api/v1/files/7/content");
	}

	private FileProperties propertiesWithBaseUrl(String baseUrl) {
		return new FileProperties(
				FileProperties.StorageType.LOCAL,
				Path.of("./data/files"),
				DataSize.ofMegabytes(10),
				Set.of("image/jpeg", "image/jpg", "image/png", "image/webp"),
				baseUrl,
				new FileProperties.S3("bucket", "dev", Duration.ofMinutes(5)));
	}
}
