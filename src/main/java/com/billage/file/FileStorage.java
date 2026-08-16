package com.billage.file;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 바이너리 저장소. 현재 구현은 서버 디스크({@link LocalFileStorage})이며,
 * Object Storage 로 옮길 때 이 인터페이스 구현만 교체한다.
 */
public interface FileStorage {

	/**
	 * 파일을 저장하고 저장소 키를 반환한다.
	 *
	 * @param key 저장소 내 경로. 원본 파일명을 그대로 쓰지 않는다.
	 */
	void store(String key, MultipartFile file);

	Resource load(String key);

	void delete(String key);
}
