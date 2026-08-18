package com.billage.folder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.billage.folder.dto.FolderUpdateRequest;
import com.billage.support.IntegrationTest;

import tools.jackson.databind.ObjectMapper;

/**
 * 폴더 수정 요청의 3가지 상태 구분 검증.
 * 미전달 = 이동 안 함 / JSON null = 최상위로 이동 / 값 = 그 폴더로 이동.
 *
 * <p>이 구분은 JSON 역직렬화 설정에 전적으로 의존하므로 다른 경로로는 검증할 수 없다.
 * 애플리케이션이 실제로 쓰는 ObjectMapper 로 검증해야 설정이 빠졌을 때 잡힌다
 * (`spring.jackson.deserialization.use-null-for-missing-reference-values`).
 */
class FolderUpdateRequestBindingTest extends IntegrationTest {

	@Autowired
	ObjectMapper mapper;

	@Test
	void 상위폴더_미전달이면_이동하지_않는다() {
		FolderUpdateRequest request = mapper.readValue("{\"name\":\"새이름\"}", FolderUpdateRequest.class);

		assertThat(request.moveRequested()).isFalse();
	}

	@Test
	void 상위폴더에_null을_보내면_최상위로_이동한다() {
		FolderUpdateRequest request = mapper.readValue("{\"parentFolderId\":null}", FolderUpdateRequest.class);

		assertThat(request.moveRequested()).isTrue();
		assertThat(request.targetParentId()).isNull();
	}

	@Test
	void 상위폴더에_값을_보내면_그_폴더로_이동한다() {
		FolderUpdateRequest request = mapper.readValue("{\"parentFolderId\":7}", FolderUpdateRequest.class);

		assertThat(request.moveRequested()).isTrue();
		assertThat(request.targetParentId()).isEqualTo(7L);
	}
}
