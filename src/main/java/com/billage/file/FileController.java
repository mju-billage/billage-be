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
	 * 파일 내려받기. 권한을 확인한 뒤 저장소에서 읽어 직접 흘려보낸다 — 저장소(MinIO)를 외부에 열지 않으므로
	 * 리다이렉트할 주소가 없다. 본문은 스트림으로 전달하고, 헤더는 업로드 때 기록한 메타데이터로 채운다
	 * (스트림 리소스는 길이를 알 수 없어 Content-Length 를 직접 준다).
	 * 인증된 요청만 허용하므로 응답을 캐시하지 않는다.
	 */
	@GetMapping("/{fileId}/content")
	public ResponseEntity<Resource> download(@CurrentUserId Long userId, @PathVariable Long fileId) {
		UploadedFile file = fileService.getAccessibleFile(fileId, userId);

		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.getContentType()))
				.contentLength(file.getSize())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"inline; filename=\"" + file.getOriginalFileName() + "\"")
				.body(fileService.loadContent(file));
	}

	@DeleteMapping("/{fileId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUserId Long userId, @PathVariable Long fileId) {
		fileService.delete(fileId, userId);
	}
}
