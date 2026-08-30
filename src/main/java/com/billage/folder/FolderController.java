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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.folder.dto.FolderCreateResponse;
import com.billage.folder.dto.FolderItemMoveRequest;
import com.billage.folder.dto.FolderItemMoveResponse;
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

	@PostMapping("/api/v1/groups/{groupId}/folders")
	public ResponseEntity<ApiResponse<FolderCreateResponse>> create(@CurrentUserId Long userId,
			@PathVariable Long groupId, @Valid @RequestBody FolderCreateRequest request) {
		FolderCreateResponse response = folderService.create(groupId, userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "폴더 생성에 성공했습니다."));
	}

	/**
	 * 폴더·장부 선택 이동. 여러 항목을 한 번에 목적지 폴더로 옮긴다.
	 * {@code targetFolderId} 를 null 로 보내면 최상위 영역으로 올린다.
	 */
	@PostMapping("/api/v1/groups/{groupId}/folder-items/move")
	public ResponseEntity<ApiResponse<FolderItemMoveResponse>> moveItems(@CurrentUserId Long userId,
			@PathVariable Long groupId, @Valid @RequestBody FolderItemMoveRequest request) {
		FolderItemMoveResponse response = folderService.moveItems(groupId, userId, request);
		int moved = response.movedFolderCount() + response.movedLedgerCount();
		return ResponseEntity.ok(ApiResponse.of(response, moved + "개 항목을 이동했습니다."));
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
