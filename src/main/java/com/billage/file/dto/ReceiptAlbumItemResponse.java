package com.billage.file.dto;

import java.time.LocalDate;

import com.billage.file.UploadedFile;

/**
 * 증빙자료 앨범의 썸네일 한 장.
 *
 * <p>원본 내역 정보를 함께 담는다 — 상세 화면의 앱바가 내역명과 등록일을 보여 주고,
 * 하단 버튼이 {@code entryId} 로 내역 상세로 이동한다.
 */
public record ReceiptAlbumItemResponse(
		Long fileId,
		String fileUrl,
		Long entryId,
		String entryTitle,
		LocalDate occurredOn,
		Long ledgerId,
		String ledgerName
) {

	public static ReceiptAlbumItemResponse of(UploadedFile file, String fileUrl) {
		var entry = file.getEntry();
		return new ReceiptAlbumItemResponse(file.getId(), fileUrl, entry.getId(), entry.getTitle(),
				entry.getOccurredOn(), entry.getLedger().getId(), entry.getLedger().getName());
	}
}
