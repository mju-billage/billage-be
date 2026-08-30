package com.billage.folder;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.folder.dto.FolderCreateResponse;
import com.billage.folder.dto.FolderItemListResponse;
import com.billage.folder.dto.FolderTreeResponse;
import com.billage.folder.dto.FolderUpdateRequest;
import com.billage.folder.dto.FolderUpdateResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FolderController {

	private final FolderService folderService;

	@GetMapping("/api/v1/groups/{groupId}/folders")
	public ResponseEntity<ApiResponse<List<FolderTreeResponse>>> getFolders(@CurrentUserId Long userId,
			@PathVariable Long groupId) {
		List<FolderTreeResponse> folders = folderService.getFolderTree(groupId, userId);
		String message = folders.isEmpty() ? "조회된 데이터가 없습니다." : "폴더 목록 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(folders, message));
	}

	/**
	 * 폴더 화면의 한 계층(폴더 + 장부). {@code folderId} 를 주지 않으면 최상위 영역이다.
	 *
	 * <p>폴더 트리 전체가 필요할 때는 위의 {@code /folders} 를 쓴다 — 이쪽은 한 계층만 본다.
	 */
	@GetMapping("/api/v1/groups/{groupId}/folder-items")
	public ResponseEntity<ApiResponse<FolderItemListResponse>> getFolderItems(@CurrentUserId Long userId,
			@PathVariable Long groupId,
			@RequestParam(required = false) Long folderId,
			@RequestParam(required = false) String keyword) {
		FolderItemListResponse items = folderService.getFolderItems(groupId, userId, folderId, keyword);
		String message = items.items().isEmpty() ? "조회된 데이터가 없습니다." : "폴더 항목 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(items, message));
	}

	@PostMapping("/api/v1/groups/{groupId}/folders")
	public ResponseEntity<ApiResponse<FolderCreateResponse>> create(@CurrentUserId Long userId,
			@PathVariable Long groupId, @Valid @RequestBody FolderCreateRequest request) {
		FolderCreateResponse response = folderService.create(groupId, userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "폴더 생성에 성공했습니다."));
	}

	@PatchMapping("/api/v1/folders/{folderId}")
	public ResponseEntity<ApiResponse<FolderUpdateResponse>> update(@CurrentUserId Long userId,
			@PathVariable Long folderId, @Valid @RequestBody FolderUpdateRequest request) {
		return ResponseEntity.ok(
				ApiResponse.of(folderService.update(folderId, userId, request), "폴더 수정에 성공했습니다."));
	}

	@DeleteMapping("/api/v1/folders/{folderId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUserId Long userId, @PathVariable Long folderId) {
		folderService.delete(folderId, userId);
	}
}
