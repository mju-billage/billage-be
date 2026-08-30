package com.billage.folder.dto;

import java.util.List;

/**
 * 폴더 화면(FDR-1-PAGE-01-0 / FDR-2-PAGE-04-0) 응답.
 *
 * <p>{@code totalCount} 는 폴더와 장부를 합친 수다. 화면 상단이 "{N}개"로 보여 주며 0건일 때도
 * 숨기지 않고 "0개"로 표기하므로, 빈 목록에서도 값을 함께 내려 준다.
 */
public record FolderItemListResponse(
		long totalCount,
		List<FolderItemResponse> items
) {

	public static FolderItemListResponse of(List<FolderItemResponse> items) {
		return new FolderItemListResponse(items.size(), items);
	}
}
