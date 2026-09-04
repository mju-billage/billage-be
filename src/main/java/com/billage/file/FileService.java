package com.billage.file;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.response.PageResponse;
import com.billage.archive.ArchiveEntry;
import com.billage.entry.Entry;
import com.billage.entry.EntryRepository;
import com.billage.entry.EntryType;
import com.billage.file.dto.FileResponse;
import com.billage.file.dto.ReceiptAlbumItemResponse;
import com.billage.file.dto.ReceiptFileResponse;
import com.billage.membership.GroupAccessGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 파일 업로드·조회·삭제와 내역 증빙 연결.
 *
 * <p>접근 권한: 아직 어디에도 연결되지 않은 파일은 업로더만, 내역에 연결된 파일은 그 모임의 관리자면 볼 수 있다.
 * 삭제는 업로더만 가능하며 연결된 파일은 먼저 연결을 끊어야 한다({@code FILE_IN_USE}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

	private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	private final FileRepository fileRepository;
	private final EntryRepository entryRepository;
	private final FileStorage fileStorage;
	private final FileProperties properties;
	private final FileUrlResolver fileUrlResolver;
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

		if (file.getEntry() != null) {
			guard.requireMembership(file.getEntry().getGroupId(), userId);
		} else if (file.getGroupId() != null) {
			// 모임 대표 이미지는 그 모임 관리자 전원이 볼 수 있어야 한다(업로더 본인만이면 목록 화면이 깨진다).
			guard.requireMembership(file.getGroupId(), userId);
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
		// 조건부 삭제로 선점과 겨룬다. 먼저 읽고 나중에 지우면 그 사이 대표 이미지가 된 파일까지 지워 버린다.
		if (fileRepository.deleteIfUnused(fileId) == 0) {
			throw new BusinessException(ErrorCode.FILE_IN_USE);
		}
		fileStorage.delete(file.getStorageKey());
	}

	/** 내역 하나에 붙일 수 있는 증빙 장수. 화면의 증빙 자료 카운터가 {@code 0/10} 이다. */
	static final int MAX_RECEIPTS = 10;

	/**
	 * 증빙 파일을 내역에 연결한다. 업로더 본인의 RECEIPT 파일이면서 아직 연결되지 않은 것만 허용한다.
	 * 이미 붙어 있는 증빙까지 합쳐 {@value #MAX_RECEIPTS} 장을 넘길 수 없다.
	 */
	@Transactional
	public void linkReceipts(Entry entry, List<Long> fileIds, Long userId) {
		if (fileIds == null || fileIds.isEmpty()) {
			return;
		}
		// 내역 행을 먼저 잠가 "세고 나서 붙이는" 사이에 다른 요청이 끼어들지 못하게 한다.
		entryRepository.findByIdForUpdate(entry.getId());
		if (fileRepository.findByEntryIdForUpdate(entry.getId()).size() + fileIds.size() > MAX_RECEIPTS) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST,
					"증빙 자료는 최대 " + MAX_RECEIPTS + "장까지 첨부할 수 있습니다.");
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

	/**
	 * 모임 대표 이미지 지정. 아직 임자가 없는 본인의 GROUP_IMAGE 파일만 쓸 수 있다.
	 * 선점은 조건부 UPDATE 한 줄로 원자적으로 끝나므로 잠금이 필요 없다.
	 */
	@Transactional
	public void claimGroupImage(Long fileId, Long groupId, Long userId) {
		try {
			if (fileRepository.claimGroupImage(fileId, groupId, userId) == 1) {
				return;
			}
		} catch (DataIntegrityViolationException e) {
			// 이미지 없는 모임에 둘이 동시에 다른 파일을 지정하면 uk_file_group 에 걸린다. 500 대신 409.
			throw new BusinessException(ErrorCode.FILE_IN_USE);
		}
		// 실패 이유를 구분해 돌려준다.
		UploadedFile file = fileRepository.findById(fileId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
		if (file.getPurpose() != FilePurpose.GROUP_IMAGE) {
			throw new BusinessException(ErrorCode.INVALID_FILE_PURPOSE);
		}
		if (!file.isUploadedBy(userId)) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}
		// 이미 다른 모임(또는 내역)이 쓰는 파일이다. 공유하면 한쪽이 지울 때 다른 쪽 참조가 깨진다.
		throw new BusinessException(ErrorCode.FILE_IN_USE);
	}

	/** 모임의 현재 대표 이미지를 떼어내 완전히 지운다. 없으면 아무 일도 하지 않는다. */
	@Transactional
	public void deleteGroupImage(Long groupId) {
		fileRepository.findGroupImage(groupId).ifPresent(file -> deleteAll(List.of(file)));
	}

	/** 모임의 현재 대표 이미지. 바뀌는지 판단할 때 쓴다. */
	@Transactional(readOnly = true)
	public Optional<UploadedFile> findGroupImage(Long groupId) {
		return fileRepository.findGroupImage(groupId);
	}

	/**
	 * 대표 이미지를 모임에서 떼어내기만 한다(파일은 남는다). 교체 중간 단계로 쓴다.
	 * 저장소 객체를 여기서 지우지 않는 것이 핵심이다 — 뒤이은 선점이 실패하면 DB 는 롤백되지만
	 * 저장소 삭제는 되돌릴 수 없어, 되살아난 참조가 빈 파일을 가리키게 된다.
	 */
	@Transactional
	public Optional<UploadedFile> detachGroupImage(Long groupId) {
		Optional<UploadedFile> current = fileRepository.findGroupImage(groupId);
		if (current.isPresent() && fileRepository.detachGroupImage(groupId, current.get().getId()) == 0) {
			// 그 사이 다른 요청이 이미지를 바꿨다. 덮어쓰면 남의 파일을 떼어 주인 없는 파일이 남는다.
			throw new BusinessException(ErrorCode.FILE_IN_USE);
		}
		return current;
	}

	/** 임자가 없어진 파일을 실제로 지운다. 교체가 확정된 뒤에 호출한다. */
	@Transactional
	public void deleteDetached(UploadedFile file) {
		deleteAll(List.of(file));
	}

	/** 모임의 대표 이미지 URL. 없으면 null. */
	@Transactional(readOnly = true)
	public String groupImageUrl(Long groupId) {
		return fileRepository.findGroupImage(groupId).map(file -> fileUrl(file.getId())).orElse(null);
	}

	/** 여러 모임의 대표 이미지 URL. 목록 조회에서 N+1 을 피하려고 한 번에 가져온다. */
	@Transactional(readOnly = true)
	public Map<Long, String> groupImageUrls(List<Long> groupIds) {
		if (groupIds.isEmpty()) {
			return Map.of();
		}
		return fileRepository.findGroupImages(groupIds).stream()
				.collect(Collectors.toMap(UploadedFile::getGroupId, file -> fileUrl(file.getId())));
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

	/**
	 * 증빙자료 앨범(「더보기 &gt; 증빙자료 앨범」). 모임의 증빙을 원본 내역과 함께 최신 발생일 순으로 모아 본다.
	 *
	 * <p>지금까지는 내역을 하나씩 열어야만 증빙을 볼 수 있었다. 필터·검색 조건은 내역 목록과 같은 것을
	 * 쓴다 — 화면도 "내역 조회 공통 로직과 완벽하게 동일하게 작동"이라 적어 두었다.
	 *
	 * <p>승인 대기 내역의 증빙도 함께 담는다. 화면에 승인 상태 표시가 없어 가릴 근거가 없고,
	 * 가리면 총무가 올린 것과 일반 관리자가 올린 것이 앨범에서 달리 보이게 된다.
	 */
	@Transactional(readOnly = true)
	public PageResponse<ReceiptAlbumItemResponse> getReceiptAlbum(Long groupId, Long userId, List<Long> ledgerIds,
			EntryType type, LocalDate from, LocalDate to, String keyword, Pageable pageable) {
		guard.requireMembership(groupId, userId);

		// JPQL 의 in 은 빈 컬렉션을 받으면 문법 오류가 난다. '장부 미선택 = 전체'이므로 조건에서 뺀다.
		List<Long> targetLedgerIds = (ledgerIds == null || ledgerIds.isEmpty()) ? null : ledgerIds;
		String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(ErrorCode.INVALID_QUERY_PARAMETER);
		}

		return PageResponse.of(
				fileRepository.searchReceipts(groupId, targetLedgerIds, type, from, to, normalizedKeyword, pageable),
				file -> ReceiptAlbumItemResponse.of(file, fileUrl(file.getId())));
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
		deleteAll(fileRepository.findReceiptsByGroupId(groupId));
	}

	/**
	 * 기록 보관 시 증빙의 주인을 원본 내역에서 보관된 내역으로 옮긴다.
	 * 옮기지 않고 원본을 지우면 "보관"인데 증빙 이미지가 사라진다.
	 */
	@Transactional
	public void moveReceiptsToArchive(Map<Long, ArchiveEntry> archiveEntryByEntryId) {
		if (archiveEntryByEntryId.isEmpty()) {
			return;
		}
		for (UploadedFile file : fileRepository.findByEntryIds(archiveEntryByEntryId.keySet())) {
			ArchiveEntry target = archiveEntryByEntryId.get(file.getEntry().getId());
			if (target != null) {
				file.moveToArchive(target);
			}
		}
	}

	/** 보관 상세가 쓰는 증빙 목록. 보관된 내역 ID 로 묶어서 준다. */
	@Transactional(readOnly = true)
	public Map<Long, List<ReceiptFileResponse>> getArchiveReceipts(Collection<Long> archiveEntryIds) {
		if (archiveEntryIds.isEmpty()) {
			return Map.of();
		}
		return fileRepository.findByArchiveEntryIds(archiveEntryIds).stream()
				.collect(Collectors.groupingBy(file -> file.getArchiveEntry().getId(),
						Collectors.mapping(file -> ReceiptFileResponse.of(file, fileUrl(file.getId())),
								Collectors.toList())));
	}

	/** 보관 기록 삭제. 그 안에 담긴 증빙도 저장소에서 함께 지운다. */
	@Transactional
	public void deleteByArchive(Long archiveId) {
		deleteAll(fileRepository.findByArchiveId(archiveId));
	}

	/**
	 * DB 행을 지우고, 저장소 객체는 <b>커밋 이후</b>에 지운다.
	 * 저장소 삭제는 롤백되지 않으므로 트랜잭션이 열려 있는 동안 지우면,
	 * 뒤늦게 롤백됐을 때 되살아난 참조가 빈 파일을 가리킨다.
	 */
	private void deleteAll(List<UploadedFile> files) {
		if (files.isEmpty()) {
			return;
		}
		fileRepository.deleteAll(files);
		fileRepository.flush();

		List<String> keys = files.stream().map(UploadedFile::getStorageKey).toList();
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			keys.forEach(fileStorage::delete);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				keys.forEach(FileService.this::deleteQuietly);
			}
		});
	}

	/**
	 * 커밋 이후의 저장소 삭제. 여기서 터져도 예외를 올리지 않는다 —
	 * 이미 커밋된 요청을 실패로 보고하게 되고, 배치 뒤쪽 키까지 건너뛰기 때문이다.
	 * 남은 객체는 메타데이터 없는 고아가 되므로 로그로 남겨 수동 정리한다.
	 */
	private void deleteQuietly(String storageKey) {
		try {
			fileStorage.delete(storageKey);
		} catch (RuntimeException e) {
			log.warn("저장소 객체 삭제 실패. 메타데이터는 이미 지워졌으니 수동 정리가 필요하다: {}", storageKey, e);
		}
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
		return fileUrlResolver.resolve(fileId);
	}
}
