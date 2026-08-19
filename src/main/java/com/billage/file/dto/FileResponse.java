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
		OffsetDateTime createdAt
) {

	public static FileResponse of(UploadedFile file, String fileUrl) {
		return new FileResponse(file.getId(), file.getPurpose(), file.getOriginalFileName(),
				file.getContentType(), file.getSize(), fileUrl, KoreanTime.toOffset(file.getCreatedAt()));
	}
}
