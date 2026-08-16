package com.billage.folder.dto;

import java.util.List;

/**
 * 폴더 트리 항목. {@code childFolders} 는 하위 폴더가 없으면 빈 배열,
 * {@code ledgerCount} 는 해당 폴더에 직접 속한 장부 수(하위 폴더는 포함하지 않는다).
 */
public record FolderTreeResponse(
		Long folderId,
		String name,
		Long parentFolderId,
		List<FolderTreeResponse> childFolders,
		long ledgerCount
) {
}
