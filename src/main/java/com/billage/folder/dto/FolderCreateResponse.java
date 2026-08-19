package com.billage.folder.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.folder.Folder;

public record FolderCreateResponse(
		Long folderId,
		Long groupId,
		String name,
		Long parentFolderId,
		OffsetDateTime createdAt
) {

	public static FolderCreateResponse from(Folder folder) {
		return new FolderCreateResponse(folder.getId(), folder.getGroup().getId(), folder.getName(),
				folder.getParentId(), KoreanTime.toOffset(folder.getCreatedAt()));
	}
}
