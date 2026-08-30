package com.billage.folder.dto;

import java.util.List;

/**
 * 폴더·장부 선택 이동 요청. 「폴더 > 메뉴 > 선택 이동」에서 다중 선택한 항목을 목적지 폴더로 옮긴다.
 *
 * <p>폴더 수정·장부 수정으로 하나씩 옮기면 중간에 실패했을 때 절반만 이동한 상태가 남는다.
 *
 * @param targetFolderId 목적지. {@code null} 이면 최상위 영역으로 옮긴다.
 */
public record FolderItemMoveRequest(
		List<Long> folderIds,
		List<Long> ledgerIds,
		Long targetFolderId
) {

	public List<Long> safeFolderIds() {
		return folderIds == null ? List.of() : folderIds;
	}

	public List<Long> safeLedgerIds() {
		return ledgerIds == null ? List.of() : ledgerIds;
	}
}
