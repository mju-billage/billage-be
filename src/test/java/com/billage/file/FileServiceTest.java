package com.billage.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.entry.EntryService;
import com.billage.entry.EntryType;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.entry.dto.EntryUpdateRequest;
import com.billage.folder.FolderService;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.membership.GroupMembershipService;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 파일 업로드 검증, 증빙 연결 규칙, 연결된 파일 삭제 차단 검증.
 */
class FileServiceTest extends IntegrationTest {

	@Autowired
	FileService fileService;
	@Autowired
	FileRepository fileRepository;
	@Autowired
	FileStorage fileStorage;
	@Autowired
	GroupService groupService;
	@Autowired
	GroupMembershipService groupMembershipService;
	@Autowired
	FolderService folderService;
	@Autowired
	LedgerService ledgerService;
	@Autowired
	EntryService entryService;
	@Autowired
	UserRepository userRepository;

	private Long ownerId;
	private Long adminId;
	private Long outsiderId;
	private Long groupId;
	private Long ledgerId;

	@BeforeEach
	void setUp() {
		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "encoded", "일반관리자")).getId();
		outsiderId = userRepository.save(User.create("outsider@example.com", "encoded", "남의모임")).getId();

		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(adminId, code);

		Long folderId = folderService.create(groupId, ownerId,
				new FolderCreateRequest("2026년 상반기", null)).folderId();
		ledgerId = ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("운영 장부", null)).ledgerId();
	}

	// --- 업로드 검증 ---

	@Test
	void 허용되지_않은_형식은_거부된다() {
		MockMultipartFile pdf = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "data".getBytes());

		assertThatThrownBy(() -> fileService.upload(ownerId, pdf, FilePurpose.RECEIPT))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
	}

	@Test
	void 빈_파일은_거부된다() {
		MockMultipartFile empty = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[0]);

		assertThatThrownBy(() -> fileService.upload(ownerId, empty, FilePurpose.RECEIPT))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_FILE);
	}

	@Test
	void 원본_파일명은_저장소_키로_쓰지_않는다() {
		Long fileId = upload(ownerId, "../../etc/passwd.jpg");

		UploadedFile file = fileRepository.findById(fileId).orElseThrow();
		assertThat(file.getOriginalFileName()).isEqualTo("passwd.jpg");
		assertThat(file.getStorageKey()).startsWith("receipt/").endsWith(".jpg");
		assertThat(file.getStorageKey()).doesNotContain("..");
	}

	@Test
	void 업로드한_파일은_다시_읽을_수_있다() {
		Long fileId = upload(ownerId, "receipt.jpg");

		UploadedFile file = fileService.getAccessibleFile(fileId, ownerId);
		assertThat(fileService.loadContent(file).exists()).isTrue();
	}

	// --- 접근 권한 ---

	@Test
	void 연결되지_않은_파일은_업로더만_볼_수_있다() {
		Long fileId = upload(ownerId, "receipt.jpg");

		assertThatThrownBy(() -> fileService.getAccessibleFile(fileId, adminId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 내역에_연결된_파일은_같은_모임_관리자면_볼_수_있다() {
		Long fileId = upload(ownerId, "receipt.jpg");
		createEntryWithReceipts(ownerId, List.of(fileId));

		assertThat(fileService.getAccessibleFile(fileId, adminId)).isNotNull();
		assertThatThrownBy(() -> fileService.getAccessibleFile(fileId, outsiderId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	// --- 증빙 연결 ---

	@Test
	void 증빙을_연결하면_내역_상세와_목록에_반영된다() {
		Long fileId = upload(ownerId, "receipt.jpg");
		Long entryId = createEntryWithReceipts(ownerId, List.of(fileId));

		var detail = entryService.getDetail(entryId, ownerId);
		assertThat(detail.receiptFiles()).singleElement()
				.satisfies(receipt -> {
					assertThat(receipt.fileId()).isEqualTo(fileId);
					assertThat(receipt.fileName()).isEqualTo("receipt.jpg");
					assertThat(receipt.fileUrl()).isEqualTo("/api/v1/files/" + fileId + "/content");
				});

		var list = entryService.getEntries(ledgerId, ownerId, null, null, null,
				org.springframework.data.domain.PageRequest.of(0, 20));
		assertThat(list.content().get(0).receiptCount()).isEqualTo(1);
	}

	@Test
	void 다른_사람이_올린_파일은_증빙으로_연결할_수_없다() {
		Long fileId = upload(adminId, "receipt.jpg");

		assertThatThrownBy(() -> createEntryWithReceipts(ownerId, List.of(fileId)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 이미_연결된_파일은_다시_연결할_수_없다() {
		Long fileId = upload(ownerId, "receipt.jpg");
		createEntryWithReceipts(ownerId, List.of(fileId));

		assertThatThrownBy(() -> createEntryWithReceipts(ownerId, List.of(fileId)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_IN_USE);
	}

	@Test
	void 증빙_용도가_아닌_파일은_연결할_수_없다() {
		MockMultipartFile image = new MockMultipartFile("file", "profile.png", "image/png", "image".getBytes());
		Long fileId = fileService.upload(ownerId, image, FilePurpose.PROFILE_IMAGE).fileId();

		assertThatThrownBy(() -> createEntryWithReceipts(ownerId, List.of(fileId)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_FILE_PURPOSE);
	}

	// --- 삭제 ---

	@Test
	void 연결되지_않은_파일은_업로더가_삭제할_수_있다() {
		Long fileId = upload(ownerId, "receipt.jpg");

		fileService.delete(fileId, ownerId);

		assertThat(fileRepository.findById(fileId)).isEmpty();
	}

	@Test
	void 내역에_연결된_파일은_삭제할_수_없다() {
		Long fileId = upload(ownerId, "receipt.jpg");
		createEntryWithReceipts(ownerId, List.of(fileId));

		assertThatThrownBy(() -> fileService.delete(fileId, ownerId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_IN_USE);
	}

	@Test
	void 증빙을_교체하면_목록에서_빠진_파일은_삭제된다() {
		Long oldFileId = upload(ownerId, "old.jpg");
		Long entryId = createEntryWithReceipts(ownerId, List.of(oldFileId));
		Long newFileId = upload(ownerId, "new.jpg");

		entryService.update(entryId, ownerId, new EntryUpdateRequest(null, null, null, null, null, List.of(newFileId)));

		assertThat(fileRepository.findById(oldFileId)).isEmpty();
		assertThat(entryService.getDetail(entryId, ownerId).receiptFiles()).singleElement()
				.satisfies(receipt -> assertThat(receipt.fileId()).isEqualTo(newFileId));
	}

	@Test
	void 이미_연결된_증빙을_그대로_다시_보내면_유지된다() {
		Long fileId = upload(ownerId, "receipt.jpg");
		Long entryId = createEntryWithReceipts(ownerId, List.of(fileId));
		Long extraFileId = upload(ownerId, "extra.jpg");

		entryService.update(entryId, ownerId,
				new EntryUpdateRequest(null, null, null, null, null, List.of(fileId, extraFileId)));

		assertThat(entryService.getDetail(entryId, ownerId).receiptFiles()).hasSize(2);
	}

	@Test
	void 내역을_지우면_증빙도_함께_사라진다() {
		Long fileId = upload(ownerId, "receipt.jpg");
		Long entryId = createEntryWithReceipts(ownerId, List.of(fileId));

		entryService.delete(entryId, ownerId);

		assertThat(fileRepository.findById(fileId)).isEmpty();
	}

	@Test
	void 장부를_지우면_증빙도_함께_사라진다() {
		Long fileId = upload(ownerId, "receipt.jpg");
		createEntryWithReceipts(ownerId, List.of(fileId));

		ledgerService.delete(ledgerId, ownerId);

		assertThat(fileRepository.findById(fileId)).isEmpty();
	}

	// --- 증빙자료 앨범 ---

	@Test
	void 앨범은_모임의_증빙을_최신_발생일_순으로_모으고_원본_내역을_함께_준다() {
		Long oldFileId = upload(ownerId, "old.jpg");
		entryService.create(ledgerId, ownerId, new EntryCreateRequest(EntryType.EXPENSE, "지난 지출", 100_000L,
				LocalDate.of(2026, 5, 1), null, null, List.of(oldFileId)));
		Long recentFileId = upload(ownerId, "recent.jpg");
		Long recentEntryId = entryService.create(ledgerId, ownerId, new EntryCreateRequest(EntryType.EXPENSE,
				"최근 지출", 200_000L, LocalDate.of(2026, 7, 20), null, null, List.of(recentFileId))).entryId();

		var album = fileService.getReceiptAlbum(groupId, ownerId, null, null, null, null, null,
				PageRequest.of(0, 20));

		assertThat(album.totalElements()).isEqualTo(2);
		assertThat(album.content().get(0)).satisfies(item -> {
			assertThat(item.fileId()).isEqualTo(recentFileId);
			// 상세 화면이 앱바에 내역명·등록일을 띄우고 하단 버튼으로 내역 상세로 간다.
			assertThat(item.entryId()).isEqualTo(recentEntryId);
			assertThat(item.entryTitle()).isEqualTo("최근 지출");
			assertThat(item.occurredOn()).isEqualTo(LocalDate.of(2026, 7, 20));
			assertThat(item.ledgerName()).isEqualTo("운영 장부");
			assertThat(item.fileUrl()).contains("/api/v1/files/" + recentFileId + "/content");
		});
	}

	@Test
	void 앨범_검색은_내역명과_장부명에_걸린다() {
		createEntryWithReceipts(ownerId, List.of(upload(ownerId, "a.jpg")));

		assertThat(fileService.getReceiptAlbum(groupId, ownerId, null, null, null, null, "대관",
				PageRequest.of(0, 20)).totalElements()).isEqualTo(1);
		assertThat(fileService.getReceiptAlbum(groupId, ownerId, null, null, null, null, "운영",
				PageRequest.of(0, 20)).totalElements()).isEqualTo(1);
		assertThat(fileService.getReceiptAlbum(groupId, ownerId, null, null, null, null, "없는말",
				PageRequest.of(0, 20)).totalElements()).isZero();
	}

	@Test
	void 앨범은_기간으로_거를_수_있다() {
		createEntryWithReceipts(ownerId, List.of(upload(ownerId, "a.jpg")));

		var inRange = fileService.getReceiptAlbum(groupId, ownerId, null, null,
				LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null, PageRequest.of(0, 20));
		var outOfRange = fileService.getReceiptAlbum(groupId, ownerId, null, null,
				LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, PageRequest.of(0, 20));

		assertThat(inRange.totalElements()).isEqualTo(1);
		assertThat(outOfRange.totalElements()).isZero();
	}

	@Test
	void 승인_대기_내역의_증빙도_앨범에_담긴다() {
		// 화면에 승인 상태 표시가 없어 가릴 근거가 없다.
		entryService.create(ledgerId, adminId, new EntryCreateRequest(EntryType.EXPENSE, "승인 대기", 100_000L,
				LocalDate.of(2026, 7, 20), null, null, List.of(upload(adminId, "pending.jpg"))));

		assertThat(fileService.getReceiptAlbum(groupId, ownerId, null, null, null, null, null,
				PageRequest.of(0, 20)).totalElements()).isEqualTo(1);
	}

	@Test
	void 모임_대표_이미지는_앨범에_걸리지_않는다() {
		MockMultipartFile image = new MockMultipartFile("file", "group.jpg", "image/jpeg", "bytes".getBytes());
		fileService.upload(ownerId, image, FilePurpose.GROUP_IMAGE);

		assertThat(fileService.getReceiptAlbum(groupId, ownerId, null, null, null, null, null,
				PageRequest.of(0, 20)).totalElements()).isZero();
	}

	@Test
	void 다른_모임_사람은_앨범을_볼_수_없다() {
		assertThatThrownBy(() -> fileService.getReceiptAlbum(groupId, outsiderId, null, null, null, null, null,
				PageRequest.of(0, 20)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	private Long upload(Long userId, String fileName) {
		MockMultipartFile file = new MockMultipartFile("file", fileName, "image/jpeg", "image-bytes".getBytes());
		return fileService.upload(userId, file, FilePurpose.RECEIPT).fileId();
	}

	private Long createEntryWithReceipts(Long userId, List<Long> fileIds) {
		return entryService.create(ledgerId, userId, new EntryCreateRequest(EntryType.EXPENSE, "대관료",
				500_000L, LocalDate.of(2026, 7, 20), null, null, fileIds)).entryId();
	}
}
