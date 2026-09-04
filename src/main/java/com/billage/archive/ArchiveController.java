package com.billage.archive;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.billage.archive.dto.ArchiveCreateRequest;
import com.billage.archive.dto.ArchiveDetailResponse;
import com.billage.archive.dto.ArchiveRenameRequest;
import com.billage.archive.dto.ArchiveSummaryResponse;
import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ArchiveController {

	private final ArchiveService archiveService;

	/** 「폴더 메인 > 보관」. 모임의 현재 장부 전부를 보관하고 원본을 비운다(총무 전용). */
	@PostMapping("/api/v1/groups/{groupId}/archives")
	public ResponseEntity<ApiResponse<ArchiveSummaryResponse>> create(@CurrentUserId Long userId,
			@PathVariable Long groupId, @Valid @RequestBody ArchiveCreateRequest request) {
		ArchiveSummaryResponse response = archiveService.create(groupId, userId, request.title());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "기록 보관에 성공했습니다."));
	}

	/** 「더보기 > 기록 보관」 보관함 목록. 최신 보관이 위에 온다. */
	@GetMapping("/api/v1/groups/{groupId}/archives")
	public ResponseEntity<ApiResponse<List<ArchiveSummaryResponse>>> getArchives(@CurrentUserId Long userId,
			@PathVariable Long groupId) {
		List<ArchiveSummaryResponse> archives = archiveService.getArchives(groupId, userId);
		String message = archives.isEmpty() ? "조회된 데이터가 없습니다." : "보관함 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(archives, message));
	}

	/** 「기록보기」 상세. */
	@GetMapping("/api/v1/archives/{archiveId}")
	public ResponseEntity<ApiResponse<ArchiveDetailResponse>> getDetail(@CurrentUserId Long userId,
			@PathVariable Long archiveId) {
		return ResponseEntity.ok(
				ApiResponse.of(archiveService.getDetail(archiveId, userId), "보관 기록 조회에 성공했습니다."));
	}

	@PatchMapping("/api/v1/archives/{archiveId}")
	public ResponseEntity<ApiResponse<ArchiveSummaryResponse>> rename(@CurrentUserId Long userId,
			@PathVariable Long archiveId, @Valid @RequestBody ArchiveRenameRequest request) {
		return ResponseEntity.ok(ApiResponse.of(
				archiveService.rename(archiveId, userId, request.title()), "보관 제목 변경에 성공했습니다."));
	}

	@DeleteMapping("/api/v1/archives/{archiveId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUserId Long userId, @PathVariable Long archiveId) {
		archiveService.delete(archiveId, userId);
	}
}
