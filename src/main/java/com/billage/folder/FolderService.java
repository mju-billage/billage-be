package com.billage.folder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.folder.dto.FolderCreateResponse;
import com.billage.folder.dto.FolderItemListResponse;
import com.billage.folder.dto.FolderItemResponse;
import com.billage.folder.dto.FolderTreeResponse;
import com.billage.folder.dto.FolderUpdateRequest;
import com.billage.folder.dto.FolderUpdateResponse;
import com.billage.group.GroupSpace;
import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerRepository;
import com.billage.membership.GroupAccessGuard;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FolderService {

	private final FolderRepository folderRepository;
	private final LedgerRepository ledgerRepository;
	private final GroupAccessGuard guard;

	/**
	 * 모임의 폴더 트리. 폴더 수가 많지 않으므로 한 번에 조회해 메모리에서 조립한다.
	 */
	@Transactional(readOnly = true)
	public List<FolderTreeResponse> getFolderTree(Long groupId, Long userId) {
		guard.requireMembership(groupId, userId);

		List<Folder> folders = folderRepository.findAllByGroupId(groupId);
		Map<Long, Long> ledgerCounts = ledgerRepository.countByFolder(groupId);

		Map<Long, List<Folder>> childrenByParent = new LinkedHashMap<>();
		List<Folder> roots = new ArrayList<>();
		for (Folder folder : folders) {
			Long parentId = folder.getParentId();
			if (parentId == null) {
				roots.add(folder);
			} else {
				childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(folder);
			}
		}

		return roots.stream()
				.map(root -> toTree(root, childrenByParent, ledgerCounts))
				.toList();
	}

	/**
	 * 폴더 화면의 한 계층. 폴더와 장부를 <b>한 목록에 섞어</b> 돌려준다.
	 *
	 * <p>{@code folderId} 가 null 이면 최상위 영역이다 — 폴더를 해제하면 그 안의 장부가 여기로 올라오는데,
	 * 폴더 트리와 {@code /folders/{id}/ledgers} 만으로는 이 장부들을 볼 방법이 없었다.
	 *
	 * <p>화면이 두 목록을 합치고 개수를 따로 세지 않도록 합산 개수까지 함께 낸다.
	 * 그리드/리스트 뷰 전환은 클라이언트 표시 설정이라 서버는 관여하지 않는다.
	 */
	@Transactional(readOnly = true)
	public FolderItemListResponse getFolderItems(Long groupId, Long userId, Long folderId, String keyword) {
		guard.requireMembership(groupId, userId);
		if (folderId != null) {
			// 다른 모임의 폴더 ID 로 남의 장부 목록을 들여다볼 수 없게 소속을 확인한다.
			findFolderInGroup(folderId, groupId);
		}

		String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		Map<Long, Long> childFolderCounts = folderRepository.countByParent(groupId);
		Map<Long, Long> ledgerCounts = ledgerRepository.countByFolder(groupId);

		List<FolderItemResponse> items = new ArrayList<>();
		for (Folder folder : folderRepository.findInLevel(groupId, folderId, normalizedKeyword)) {
			// 화면의 "{N}개의 항목" 은 하위 폴더와 하위 장부를 함께 센 값이다.
			long childCount = childFolderCounts.getOrDefault(folder.getId(), 0L)
					+ ledgerCounts.getOrDefault(folder.getId(), 0L);
			items.add(FolderItemResponse.of(folder, childCount));
		}
		// 폴더를 먼저, 그 다음 장부. 각 묶음 안에서는 이름 오름차순(쿼리 정렬 그대로).
		ledgerRepository.findInLevel(groupId, folderId, normalizedKeyword).stream()
				.map(FolderItemResponse::of)
				.forEach(items::add);

		return FolderItemListResponse.of(items);
	}

	@Transactional
	public FolderCreateResponse create(Long groupId, Long userId, FolderCreateRequest request) {
		GroupSpace group = guard.requireOwner(groupId, userId).getGroup();
		Folder parent = request.parentFolderId() == null ? null : findFolderInGroup(request.parentFolderId(), groupId);

		return FolderCreateResponse.from(folderRepository.save(
				Folder.create(group, parent, requireNonBlank(request.name(), "폴더 이름은 공백일 수 없습니다."))));
	}

	/**
	 * 폴더 수정. 이름 변경과 상위 폴더 이동을 함께 처리한다.
	 * 자기 자신이나 자기 하위 폴더를 상위로 지정하면 순환이 생기므로 거부한다.
	 */
	@Transactional
	public FolderUpdateResponse update(Long folderId, Long userId, FolderUpdateRequest request) {
		Folder folder = findFolder(folderId);
		guard.requireOwner(folder.getGroup().getId(), userId);

		if (request.name() != null) {
			folder.rename(requireNonBlank(request.name(), "폴더 이름은 공백일 수 없습니다."));
		}
		if (request.moveRequested()) {
			folder.moveTo(resolveNewParent(folder, request.targetParentId()));
		}

		return FolderUpdateResponse.from(folder);
	}

	/**
	 * 폴더 삭제. 폴더 구조만 제거하고 내부 장부·하위 폴더는 삭제 대상의 상위 폴더로 올린다
	 * (최상위 폴더를 지우면 내부 항목도 최상위가 된다).
	 */
	@Transactional
	public void delete(Long folderId, Long userId) {
		Folder folder = findFolder(folderId);
		guard.requireOwner(folder.getGroup().getId(), userId);

		Folder newParent = folder.getParent();
		for (Folder child : folderRepository.findChildren(folderId)) {
			child.moveTo(newParent);
		}
		for (Ledger ledger : ledgerRepository.findAllByFolderId(folderId)) {
			// 최상위 폴더를 지우면 newParent 가 null 이므로 장부는 최상위 영역으로 올라간다.
			ledger.moveTo(newParent);
		}
		folderRepository.flush();
		folderRepository.delete(folder);
	}

	private FolderTreeResponse toTree(Folder folder, Map<Long, List<Folder>> childrenByParent,
			Map<Long, Long> ledgerCounts) {
		List<FolderTreeResponse> children = childrenByParent.getOrDefault(folder.getId(), List.of()).stream()
				.map(child -> toTree(child, childrenByParent, ledgerCounts))
				.toList();

		return new FolderTreeResponse(folder.getId(), folder.getName(), folder.getParentId(), children,
				ledgerCounts.getOrDefault(folder.getId(), 0L));
	}

	private Folder resolveNewParent(Folder folder, Long parentId) {
		if (parentId == null) {
			return null;
		}
		if (parentId.equals(folder.getId())) {
			throw new BusinessException(ErrorCode.INVALID_PARENT_FOLDER);
		}
		Folder parent = findFolderInGroup(parentId, folder.getGroup().getId());
		if (isDescendant(parent, folder.getId())) {
			throw new BusinessException(ErrorCode.INVALID_PARENT_FOLDER);
		}
		return parent;
	}

	/** 후보 상위 폴더가 대상 폴더의 하위인지 확인해 순환을 막는다. */
	private boolean isDescendant(Folder candidate, Long folderId) {
		Set<Long> visited = new HashSet<>();
		for (Folder current = candidate; current != null; current = current.getParent()) {
			if (!visited.add(current.getId())) {
				break;
			}
			if (current.getId().equals(folderId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 공백 전용 이름 차단. `@Size(min = 1)` 은 " " 를 통과시켜 trim 후 빈 이름이 된다.
	 */
	private String requireNonBlank(String name, String message) {
		String trimmed = name.trim();
		if (trimmed.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
		}
		return trimmed;
	}

	private Folder findFolder(Long folderId) {
		return folderRepository.findById(folderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));
	}

	private Folder findFolderInGroup(Long folderId, Long groupId) {
		Folder folder = findFolder(folderId);
		if (!folder.getGroup().getId().equals(groupId)) {
			throw new BusinessException(ErrorCode.INVALID_PARENT_FOLDER);
		}
		return folder;
	}
}
