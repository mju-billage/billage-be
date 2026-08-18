package com.billage.folder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.billage.folder.dto.FolderUpdateRequest;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class FeatureProbeTest {

	private final JsonMapper mapper = JsonMapper.builder()
			.enable(DeserializationFeature.USE_NULL_FOR_MISSING_REFERENCE_VALUES)
			.build();

	@Test
	void probe() {
		var absent = mapper.readValue("{\"name\":\"x\"}", FolderUpdateRequest.class);
		var explicitNull = mapper.readValue("{\"parentFolderId\":null}", FolderUpdateRequest.class);
		var value = mapper.readValue("{\"parentFolderId\":7}", FolderUpdateRequest.class);
		System.out.println("PROBE absent.moveRequested=" + absent.moveRequested());
		System.out.println("PROBE null.moveRequested=" + explicitNull.moveRequested()
				+ " target=" + explicitNull.targetParentId());
		System.out.println("PROBE value.target=" + value.targetParentId());
		assertThat(true).isTrue();
	}
}
