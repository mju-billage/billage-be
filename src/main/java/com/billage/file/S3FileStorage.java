package com.billage.file;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3 저장소. 자격 증명은 EC2 인스턴스 역할에서 얻으므로 서버에 액세스 키를 두지 않는다.
 * 다운로드는 presigned URL 로 넘겨 앱이 파일 트래픽을 중계하지 않게 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "billage.file.storage", havingValue = "S3")
public class S3FileStorage implements FileStorage {

	private final S3Client s3Client;
	private final S3Presigner presigner;
	private final FileProperties properties;

	public S3FileStorage(S3Client s3Client, S3Presigner presigner, FileProperties properties) {
		this.s3Client = s3Client;
		this.presigner = presigner;
		this.properties = properties;
		if (properties.s3().bucket() == null || properties.s3().bucket().isBlank()) {
			throw new IllegalStateException("billage.file.s3.bucket 설정이 필요합니다.");
		}
	}

	@Override
	public void store(String key, MultipartFile file) {
		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucket())
				.key(objectKey(key))
				.contentType(file.getContentType())
				.contentLength(file.getSize())
				.build();
		try {
			s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
		} catch (IOException | S3Exception e) {
			log.error("S3 업로드 실패: {}", key, e);
			throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
		}
	}

	@Override
	public Resource load(String key) {
		GetObjectRequest request = GetObjectRequest.builder().bucket(bucket()).key(objectKey(key)).build();
		try {
			return new InputStreamResource(s3Client.getObject(request));
		} catch (NoSuchKeyException e) {
			throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
		}
	}

	@Override
	public void delete(String key) {
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(objectKey(key)).build());
		} catch (S3Exception e) {
			log.error("S3 삭제 실패: {}", key, e);
			throw new BusinessException(ErrorCode.FILE_DELETE_FAILED);
		}
	}

	@Override
	public Optional<URI> presignedGetUrl(String key) {
		GetObjectPresignRequest request = GetObjectPresignRequest.builder()
				.signatureDuration(properties.s3().presignExpiry())
				.getObjectRequest(builder -> builder.bucket(bucket()).key(objectKey(key)))
				.build();

		return Optional.of(presigner.presignGetObject(request).url()).map(url -> URI.create(url.toString()));
	}

	private String bucket() {
		return properties.s3().bucket();
	}

	/** dev·prod 가 한 버킷을 공유할 수 있도록 환경 프리픽스를 붙인다. */
	private String objectKey(String key) {
		String prefix = properties.s3().prefix();
		return (prefix == null || prefix.isBlank()) ? key : prefix + "/" + key;
	}
}
