package com.billage.file;

import java.net.URI;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 바이너리 저장소. 서버 디스크({@link LocalFileStorage})와 S3({@link S3FileStorage}) 구현이 있으며
 * {@code billage.file.storage} 설정으로 선택한다.
 */
public interface FileStorage {

	/**
	 * 파일을 저장한다.
	 *
	 * @param key 저장소 내 경로. 원본 파일명을 그대로 쓰지 않는다.
	 */
	void store(String key, MultipartFile file);

	Resource load(String key);

	void delete(String key);

	/**
	 * 직접 내려받을 수 있는 임시 URL. 지원하는 저장소만 값을 반환하며,
	 * 값이 있으면 컨트롤러는 파일을 흘려보내는 대신 이 URL 로 리다이렉트한다
	 * (앱 메모리·대역폭을 파일 트래픽이 통과하지 않게 하기 위함).
	 */
	default Optional<URI> presignedGetUrl(String key) {
		return Optional.empty();
	}
}
