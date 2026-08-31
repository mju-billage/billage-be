package com.billage.file;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.common.response.PageResponse;
import com.billage.entry.EntryType;
import com.billage.file.dto.ReceiptAlbumItemResponse;

import lombok.RequiredArgsConstructor;

/**
 * 증빙자료 앨범. 파일 자체를 다루는 {@link FileController} 와 달리 <b>모임 단위 조회</b>라 경로가 다르다.
 */
@RestController
@RequiredArgsConstructor
public class ReceiptAlbumController {

	private final FileService fileService;

	/**
	 * 모임의 증빙을 원본 내역과 함께 모아 본다. 필터·검색 조건은 내역 목록과 같다.
	 */
	@GetMapping("/api/v1/groups/{groupId}/receipts")
	public ResponseEntity<ApiResponse<PageResponse<ReceiptAlbumItemResponse>>> getReceiptAlbum(
			@CurrentUserId Long userId, @PathVariable Long groupId,
			@RequestParam(required = false) List<Long> ledgerIds,
			@RequestParam(required = false) EntryType type,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String keyword,
			@PageableDefault(size = 20) Pageable pageable) {
		PageResponse<ReceiptAlbumItemResponse> album =
				fileService.getReceiptAlbum(groupId, userId, ledgerIds, type, from, to, keyword, pageable);
		String message = album.content().isEmpty() ? "조회된 데이터가 없습니다." : "증빙자료 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(album, message));
	}
}
