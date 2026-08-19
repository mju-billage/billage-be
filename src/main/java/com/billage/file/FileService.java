package com.billage.file;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.entry.Entry;
import com.billage.file.dto.FileResponse;
import com.billage.file.dto.ReceiptFileResponse;
import com.billage.membership.GroupAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * 파일 업로드·조회·삭제와 내역 증빙 연결.
 *
 * <p>접근 권한: 아직 어디에도 연결되지 않은 파일은 업로더만, 내역에 연결된 파일은 그 모임의 관리자면 볼 수 있다.
 * 삭제는 업로더만 가능하며 연결된 파일은 먼저 연결을 끊어야 한다({@code FILE_IN_USE}).
 */
@Service
@RequiredArgsConstructor
public class FileService {

	private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	private final FileRepository fileRepository;
	private final FileStorage fileStorage;
	private final FileProperties properties;
	private final GroupAccessGuard guard;

	@Transactional
	public FileResponse upload(Long userId, MultipartFile file, FilePurpose purpose) {
		validate(file);

		String key = storageKey(purpose, file.getOriginalFilename());
		fileStorage.store(key, file);

		UploadedFile saved = fileRepository.save(UploadedFile.create(purpose, key,
				originalFileName(file), file.getContentType(), file.getSize(), userId));

		return FileResponse.of(saved, fileUrl(saved.getId()));
	}

	@Transactional(readOnly = true)
	public UploadedFile getAccessibleFile(Long fileId, Long userId) {
		UploadedFile file = fileRepository.findById(fileId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

		if (file.isLinked()) {
			guard.requireMembership(file.getEntry().getGroupId(), userId);
		} else if (!file.isUploadedBy(userId)) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}
		return file;
	}

	@Transactional(readOnly = true)
	public Resource loadContent(UploadedFile file) {
		return fileStorage.load(file.getStorageKey());
	}

	/** 저장소가 지원하면 직접 내려받을 임시 URL 을 준다(S3). 로컬 디스크는 비어 있다. */
	public Optional<URI> presignedUrl(UploadedFile file) {
		return fileStorage.presignedGetUrl(file.getStorageKey());
	}

	/**
	 * 파일 삭제. 내역 등에 연결된 파일은 먼저 연결을 끊어야 한다.
	 * DB 메타데이터와 저장소 객체를 함께 지운다.
	 */
	@Transactional
	public void delete(Long fileId, Long userId) {
		UploadedFile file = fileRepository.findById(fileId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
		if (!file.isUploadedBy(userId)) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}
		if (file.isLinked()) {
			throw new BusinessException(ErrorCode.FILE_IN_USE);
		}

		fileRepository.delete(file);
		fileStorage.delete(file.getStorageKey());
	}

	/**
	 * 증빙 파일을 내역에 연결한다. 업로더 본인의 RECEIPT 파일이면서 아직 연결되지 않은 것만 허용한다.
	 */
	@Transactional
	public void linkReceipts(Entry entry, List<Long> fileIds, Long userId) {
		if (fileIds == null || fileIds.isEmpty()) {
			return;
		}
		for (Long fileId : fileIds) {
			UploadedFile file = fileRepository.findById(fileId)
					.orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
			if (file.getPurpose() != FilePurpose.RECEIPT) {
				throw new BusinessException(ErrorCode.INVALID_FILE_PURPOSE);
			}
			if (!file.isUploadedBy(userId)) {
				throw new BusinessException(ErrorCode.ACCESS_DENIED);
			}
			if (file.isLinked()) {
				throw new BusinessException(ErrorCode.FILE_IN_USE);
			}
			file.linkTo(entry);
		}
	}

	/**
	 * 내역 수정 시 증빙 전체 교체. 전달된 목록이 최종 상태가 된다.
	 * 이미 연결된 파일은 그대로 두고, 목록에서 빠진 증빙은 완전 삭제하며, 새로 들어온 것만 연결 검증을 거친다.
	 * {@code fileIds} 가 null 이면 "변경 없음"이라 아무것도 하지 않는다.
	 */
	@Transactional
	public void replaceReceipts(Entry entry, List<Long> fileIds, Long userId) {
		if (fileIds == null) {
			return;
		}
		List<UploadedFile> linked = fileRepository.findByEntryId(entry.getId());

		deleteAll(linked.stream().filter(file -> !fileIds.contains(file.getId())).toList());
		linkReceipts(entry, fileIds.stream()
				.filter(fileId -> linked.stream().noneMatch(file -> file.getId().equals(fileId)))
				.toList(), userId);
	}

	/** 내역 삭제 시 그 내역에 연결된 증빙을 함께 지운다. */
	@Transactional
	public void deleteByEntry(Long entryId) {
		deleteAll(fileRepository.findByEntryId(entryId));
	}

	@Transactional(readOnly = true)
	public List<ReceiptFileResponse> getReceipts(Long entryId) {
		return fileRepository.findByEntryId(entryId).stream()
				.map(file -> ReceiptFileResponse.of(file, fileUrl(file.getId())))
				.toList();
	}

	/** 내역 목록의 receiptCount 를 N+1 없이 채우기 위해 한 번에 조회한다. */
	@Transactional(readOnly = true)
	public Map<Long, Long> countReceipts(List<Long> entryIds) {
		if (entryIds.isEmpty()) {
			return Map.of();
		}
		return fileRepository.countByEntryIdsRaw(entryIds).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
	}

	/** 장부 삭제 시 그 장부 내역에 연결된 증빙을 함께 지운다. */
	@Transactional
	public void deleteByLedger(Long ledgerId) {
		deleteAll(fileRepository.findByLedgerId(ledgerId));
	}

	/** 모임 삭제 시 그 모임 내역에 연결된 증빙을 함께 지운다. */
	@Transactional
	public void deleteByGroup(Long groupId) {
		deleteAll(fileRepository.findByGroupId(groupId));
	}

	private void deleteAll(List<UploadedFile> files) {
		if (files.isEmpty()) {
			return;
		}
		fileRepository.deleteAll(files);
		fileRepository.flush();
		files.forEach(file -> fileStorage.delete(file.getStorageKey()));
	}

	private void validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_FILE);
		}
		if (file.getSize() > properties.maxSize().toBytes()) {
			throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
		}
		String contentType = file.getContentType();
		if (contentType == null || !properties.allowedContentTypes().contains(contentType.toLowerCase())) {
			throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
		}
	}

	/** 원본 파일명을 저장소 키로 쓰지 않는다(경로 조작·중복 방지). */
	private String storageKey(FilePurpose purpose, String originalFileName) {
		return "%s/%s/%s%s".formatted(
				purpose.name().toLowerCase(),
				LocalDate.now().format(KEY_DATE),
				UUID.randomUUID(),
				extensionOf(originalFileName));
	}

	private String extensionOf(String originalFileName) {
		if (originalFileName == null) {
			return "";
		}
		int dot = originalFileName.lastIndexOf('.');
		if (dot < 0 || dot == originalFileName.length() - 1) {
			return "";
		}
		String extension = originalFileName.substring(dot + 1).toLowerCase();
		return extension.matches("[a-z0-9]{1,10}") ? "." + extension : "";
	}

	private String originalFileName(MultipartFile file) {
		String name = file.getOriginalFilename();
		if (name == null || name.isBlank()) {
			return "unnamed";
		}
		// 경로가 섞여 들어오는 클라이언트가 있어 파일명만 남긴다.
		return name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
	}

	private String fileUrl(Long fileId) {
		return "/api/v1/files/" + fileId + "/content";
	}
}
