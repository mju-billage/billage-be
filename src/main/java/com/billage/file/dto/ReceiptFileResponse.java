package com.billage.file.dto;

import com.billage.file.UploadedFile;

/** 내역에 연결된 증빙 파일 항목. */
public record ReceiptFileResponse(
		Long fileId,
		String fileName,
		String fileUrl
) {

	public static ReceiptFileResponse of(UploadedFile file, String fileUrl) {
		return new ReceiptFileResponse(file.getId(), file.getOriginalFileName(), fileUrl);
	}
}
