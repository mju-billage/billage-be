package com.billage.file;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * 파일 업로드 정책. 허용 형식·용량은 기획 확정 전 제안값이며 설정으로 바꿀 수 있다.
 *
 * @param storage             저장소 종류. 로컬 개발은 {@code LOCAL}(디스크), dev·prod 는 {@code S3}.
 * @param publicBaseUrl       응답의 파일 URL 앞에 붙일 절대 주소(예: {@code https://api.billage.app}).
 *                            비워두면 요청에서 유추한다 — 자세한 규칙은 {@code FileUrlResolver} 참고.
 * @param allowedContentTypes {@code image/jpg} 는 표준 MIME 타입이 아니지만 일부 RN 이미지 피커가 그렇게 보낸다.
 *                            흔한 오기라 415 로 막기보다 받아준다.
 */
@ConfigurationProperties(prefix = "billage.file")
public record FileProperties(
		@DefaultValue("LOCAL") StorageType storage,
		@DefaultValue("./data/files") Path storagePath,
		@DefaultValue("10MB") DataSize maxSize,
		@DefaultValue({ "image/jpeg", "image/jpg", "image/png", "image/webp" }) Set<String> allowedContentTypes,
		@DefaultValue("") String publicBaseUrl,
		@DefaultValue S3 s3
) {

	public enum StorageType {
		LOCAL,
		S3
	}

	/**
	 * @param prefix 버킷 안 최상위 경로. dev·prod 가 한 버킷을 쓸 때 환경을 구분한다.
	 * @param presignExpiry 다운로드용 presigned URL 유효 시간
	 */
	public record S3(
			String bucket,
			@DefaultValue("dev") String prefix,
			@DefaultValue("5m") Duration presignExpiry
	) {
	}
}
