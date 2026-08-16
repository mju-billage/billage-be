package com.billage.file;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.file.dto.FileResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

	private final FileService fileService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<FileResponse>> upload(@CurrentUserId Long userId,
			@RequestParam("file") MultipartFile file,
			@RequestParam("purpose") FilePurpose purpose) {
		FileResponse response = fileService.upload(userId, file, purpose);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "파일 업로드에 성공했습니다."));
	}

	/**
	 * 파일 내려받기. 인증된 요청만 허용하므로 응답을 캐시하지 않는다.
	 */
	@GetMapping("/{fileId}/content")
	public ResponseEntity<Resource> download(@CurrentUserId Long userId, @PathVariable Long fileId) {
		UploadedFile file = fileService.getAccessibleFile(fileId, userId);
		Resource resource = fileService.loadContent(file);

		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.getContentType()))
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"inline; filename=\"" + file.getOriginalFileName() + "\"")
				.body(resource);
	}

	@DeleteMapping("/{fileId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUserId Long userId, @PathVariable Long fileId) {
		fileService.delete(fileId, userId);
	}
}
