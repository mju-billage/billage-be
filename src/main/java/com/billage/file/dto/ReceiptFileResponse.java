package com.billage.file.dto;

import com.billage.file.UploadedFile;

/** 내역에 연결된 증빙 파일 항목. */
public record ReceiptFileResponse(
		Long fileId,
		String fileName,
		String fileUrl,
		/** 업로드한 바이트의 SHA-256(소문자 hex). 이 기능 이전에 올린 파일은 null. */
		String contentHash
) {

	public static ReceiptFileResponse of(UploadedFile file, String fileUrl) {
		return new ReceiptFileResponse(file.getId(), file.getOriginalFileName(), fileUrl, file.getContentHash());
	}
}
