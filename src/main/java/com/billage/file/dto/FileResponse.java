package com.billage.file.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.file.FilePurpose;
import com.billage.file.UploadedFile;

public record FileResponse(
		Long fileId,
		FilePurpose purpose,
		String originalFileName,
		String contentType,
		long size,
		String fileUrl,
		/** 업로드한 바이트의 SHA-256(소문자 hex). 같은 내역에 같은 값이 두 번 붙으면 DUPLICATE_RECEIPT_FILE. */
		String contentHash,
		OffsetDateTime createdAt
) {

	public static FileResponse of(UploadedFile file, String fileUrl) {
		return new FileResponse(file.getId(), file.getPurpose(), file.getOriginalFileName(),
				file.getContentType(), file.getSize(), fileUrl, file.getContentHash(), KoreanTime.toOffset(file.getCreatedAt()));
	}
}
