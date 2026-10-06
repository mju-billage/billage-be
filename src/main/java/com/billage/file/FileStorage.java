package com.billage.file;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 바이너리 저장소. 서버 디스크({@link LocalFileStorage})와 S3 호환 저장소({@link S3FileStorage}, 서버 안의 MinIO) 구현이 있으며
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
}
