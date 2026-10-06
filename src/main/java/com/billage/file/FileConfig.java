package com.billage.file;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileConfig {

	/**
	 * S3 호환 저장소(MinIO) 클라이언트. AWS S3 와 다른 점:
	 * <ul>
	 *   <li>엔드포인트를 명시한다. 기본값은 AWS S3 주소다.</li>
	 *   <li>액세스 키로 인증한다. 인스턴스 역할이 없어 기본 자격 증명 체인을 쓸 수 없다.</li>
	 *   <li>버킷을 경로에 담는다(path-style). 가상 호스트 방식은 버킷별 DNS 가 있어야 한다.</li>
	 * </ul>
	 */
	@Bean
	@ConditionalOnProperty(name = "billage.file.storage", havingValue = "S3")
	S3Client s3Client(FileProperties properties) {
		FileProperties.S3 s3 = properties.s3();
		requireText(s3.endpoint(), "billage.file.s3.endpoint", "S3_ENDPOINT");
		requireText(s3.accessKey(), "billage.file.s3.access-key", "S3_ACCESS_KEY");
		requireText(s3.secretKey(), "billage.file.s3.secret-key", "S3_SECRET_KEY");

		return S3Client.builder()
				.endpointOverride(URI.create(s3.endpoint().trim()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.region(Region.of(s3.region()))
				.build();
	}

	private static void requireText(String value, String property, String env) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(property + " 설정이 필요합니다. (" + env + ")");
		}
	}
}
