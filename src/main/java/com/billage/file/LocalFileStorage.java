package com.billage.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 서버 디스크 기반 저장소. Object Storage 도입 전까지 사용한다.
 * 저장 경로를 벗어나는 키(경로 탈출)는 허용하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorage {

	private final FileProperties properties;

	@Override
	public void store(String key, MultipartFile file) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			log.error("파일 저장 실패: {}", key, e);
			throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
		}
	}

	@Override
	public Resource load(String key) {
		Path path = resolve(key);
		if (!Files.exists(path)) {
			throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
		}
		return new PathResource(path);
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		} catch (IOException e) {
			log.error("파일 삭제 실패: {}", key, e);
			throw new BusinessException(ErrorCode.FILE_DELETE_FAILED);
		}
	}

	private Path resolve(String key) {
		Path root = properties.storagePath().toAbsolutePath().normalize();
		Path resolved = root.resolve(key).normalize();
		if (!resolved.startsWith(root)) {
			throw new BusinessException(ErrorCode.INVALID_FILE);
		}
		return resolved;
	}
}
