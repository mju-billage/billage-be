package com.billage.folder.dto;

/** 선택 이동 결과. 화면 스낵바가 "'{폴더명}' 폴더로 이동되었어요." 를 띄운다. */
public record FolderItemMoveResponse(
		int movedFolderCount,
		int movedLedgerCount,
		Long targetFolderId,
		String targetFolderName
) {
}
