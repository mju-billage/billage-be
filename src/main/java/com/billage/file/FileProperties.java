package com.billage.file;

import java.nio.file.Path;
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
	 * S3 호환 저장소. 서버 안의 MinIO 를 쓴다 — AWS SDK for S3 는 그대로 쓰고 엔드포인트·자격 증명만 명시한다.
	 *
	 * @param endpoint  S3 API 주소(예: {@code http://127.0.0.1:9000}). MinIO 는 외부에 열지 않으므로 서버 내부 주소다.
	 * @param accessKey 앱 전용 액세스 키. 커밋 금지 — 환경변수로만 주입한다.
	 * @param secretKey 앱 전용 시크릿 키. 커밋 금지.
	 * @param region    SigV4 서명에 쓰는 리전. MinIO 기본값이 {@code us-east-1} 이다.
	 * @param prefix    버킷 안 최상위 경로. dev·prod 가 한 버킷을 쓸 때 환경을 구분한다.
	 */
	public record S3(
			String endpoint,
			String accessKey,
			String secretKey,
			String bucket,
			@DefaultValue("us-east-1") String region,
			@DefaultValue("dev") String prefix
	) {
	}
}
