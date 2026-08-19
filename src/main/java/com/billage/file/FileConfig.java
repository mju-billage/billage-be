package com.billage.file;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileConfig {

	/**
	 * 자격 증명·리전은 기본 체인에서 해석한다 — EC2 는 인스턴스 역할, 로컬은 개발자 프로필.
	 * 서버에 액세스 키를 저장하지 않는다.
	 */
	@Bean
	@ConditionalOnProperty(name = "billage.file.storage", havingValue = "S3")
	S3Client s3Client() {
		return S3Client.create();
	}

	@Bean
	@ConditionalOnProperty(name = "billage.file.storage", havingValue = "S3")
	S3Presigner s3Presigner() {
		return S3Presigner.create();
	}
}
