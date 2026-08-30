package com.billage.folder.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.folder.Folder;
import com.billage.ledger.Ledger;

/**
 * 폴더 화면의 항목 하나. 폴더와 장부가 한 그리드에 섞여 나오므로 두 종류를 같은 모양으로 담는다.
 *
 * <p>화면은 {@code itemType} 으로 아이콘과 터치 동작을 나눈다 — 폴더는 안으로 들어가고(뎁스인)
 * 장부는 장부 상세로 간다.
 */
public record FolderItemResponse(
		ItemType itemType,
		Long id,
		String name,
		/** 폴더만 채운다 — 하위 폴더 + 하위 장부의 합. 장부는 null 이고 화면이 대신 생성일을 보여 준다. */
		Long childCount,
		OffsetDateTime createdAt
) {

	public enum ItemType {
		FOLDER,
		LEDGER
	}

	public static FolderItemResponse of(Folder folder, long childCount) {
		return new FolderItemResponse(ItemType.FOLDER, folder.getId(), folder.getName(), childCount,
				KoreanTime.toOffset(folder.getCreatedAt()));
	}

	public static FolderItemResponse of(Ledger ledger) {
		return new FolderItemResponse(ItemType.LEDGER, ledger.getId(), ledger.getName(), null,
				KoreanTime.toOffset(ledger.getCreatedAt()));
	}
}
