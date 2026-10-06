package com.billage.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

/**
 * 실제 MinIO(S3 호환 서버) 위에서 저장소 구현을 확인한다.
 * 엔드포인트 지정·path-style·액세스 키 인증은 클라이언트 설정에 달려 있어, 서버 없이 검증할 수 없다.
 */
@Testcontainers
class S3FileStorageMinioTest {

	private static final String ACCESS_KEY = "test-access-key";
	private static final String SECRET_KEY = "test-secret-key";
	private static final String BUCKET = "billage";

	/** 서버(deploy/compose.minio.yaml)와 같은 이미지·태그를 쓴다. */
	@Container
	static final GenericContainer<?> MINIO = new GenericContainer<>("pgsty/silo:RELEASE.2026-09-16T00-00-00Z")
			.withEnv("MINIO_ROOT_USER", ACCESS_KEY)
			.withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
			.withCommand("server", "/data")
			.withExposedPorts(9000)
			.waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

	static S3Client client;
	static S3FileStorage storage;

	@BeforeAll
	static void setUp() {
		FileProperties properties = new FileProperties(
				FileProperties.StorageType.S3,
				Path.of("./data/files"),
				DataSize.ofMegabytes(10),
				Set.of("image/jpeg", "image/png"),
				"",
				new FileProperties.S3("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000),
						ACCESS_KEY, SECRET_KEY, BUCKET, "us-east-1", "dev"));

		client = new FileConfig().s3Client(properties);
		client.createBucket(builder -> builder.bucket(BUCKET));
		storage = new S3FileStorage(client, properties);
	}

	@Test
	@DisplayName("올린 파일을 그대로 읽을 수 있고, 환경 프리픽스 아래에 형식과 함께 저장된다")
	void storesAndLoads() throws IOException {
		byte[] content = "fake-png-bytes".getBytes();
		String key = "receipt/2026/10/06/store-and-load.png";

		storage.store(key, new MockMultipartFile("file", "영수증.png", "image/png", content));

		try (InputStream in = storage.load(key).getInputStream()) {
			assertThat(in.readAllBytes()).isEqualTo(content);
		}
		HeadObjectResponse head = client.headObject(builder -> builder.bucket(BUCKET).key("dev/" + key));
		assertThat(head.contentType()).isEqualTo("image/png");
		assertThat(head.contentLength()).isEqualTo(content.length);
	}

	@Test
	@DisplayName("지운 파일은 더 이상 읽히지 않는다")
	void deletes() {
		String key = "receipt/2026/10/06/delete.png";
		storage.store(key, new MockMultipartFile("file", "a.png", "image/png", "data".getBytes()));

		storage.delete(key);

		assertThatThrownBy(() -> storage.load(key))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_NOT_FOUND);
	}

	@Test
	@DisplayName("없는 키를 지워도 실패하지 않는다 — 롤백 정리가 쓰이지 않은 키를 지우는 경우가 있다")
	void deletingMissingKeyIsNoop() {
		storage.delete("receipt/2026/10/06/never-written.png");
	}
}
