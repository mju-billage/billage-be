package com.billage.folder.dto;

import com.billage.folder.Folder;

public record FolderUpdateResponse(
		Long folderId,
		String name,
		Long parentFolderId
) {

	public static FolderUpdateResponse from(Folder folder) {
		return new FolderUpdateResponse(folder.getId(), folder.getName(), folder.getParentId());
	}
}
