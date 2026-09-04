package com.billage.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dues.DuesService;
import com.billage.dues.dto.PaymentStatusUpdateRequest;
import com.billage.file.FilePurpose;
import com.billage.file.FileRepository;
import com.billage.file.FileService;
import com.billage.dues.dto.DuesCreateRequest;
import com.billage.entry.ApprovalStatus;
import com.billage.entry.EntryRepository;
import com.billage.entry.EntryService;
import com.billage.entry.EntryType;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.folder.FolderRepository;
import com.billage.folder.FolderService;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.ledger.LedgerRepository;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.member.MemberService;
import com.billage.member.dto.MemberCreateRequest;
import com.billage.membership.GroupMembershipService;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 기록 보관. 스냅샷을 뜨고 원본을 비우는 것이 한 트랜잭션에서 함께 일어나야 한다 —
 * 하나만 되면 데이터가 사라지거나 두 벌이 된다.
 */
class ArchiveServiceTest extends IntegrationTest {

	@Autowired
	ArchiveService archiveService;
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
	DuesService duesService;
	@Autowired
	MemberService memberService;
	@Autowired
	LedgerRepository ledgerRepository;
	@Autowired
	EntryRepository entryRepository;
	@Autowired
	FolderRepository folderRepository;
	@Autowired
	FileService fileService;
	@Autowired
	FileRepository fileRepository;
	@Autowired
	UserRepository userRepository;

	private Long ownerId;
	private Long adminId;
	private Long groupId;
	private Long folderId;
	private Long ledgerId;

	@BeforeEach
	void setUp() {
		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "encoded", "일반관리자")).getId();

		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(adminId, code);

		folderId = folderService.create(groupId, ownerId, new FolderCreateRequest("2025", null)).folderId();
		ledgerId = ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("운영 장부", 1_000_000L)).ledgerId();
	}

	@Test
	void 보관하면_스냅샷이_남고_원본_폴더와_장부와_내역은_사라진다() {
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 500_000L, LocalDate.of(2026, 3, 1));
		createEntry(ownerId, EntryType.EXPENSE, "대관료", 200_000L, LocalDate.of(2026, 5, 20));

		var archived = archiveService.create(groupId, ownerId, "2025 김돌붕이");

		assertThat(archived.title()).isEqualTo("2025 김돌붕이");
		assertThat(archived.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(archived.endDate()).isEqualTo(LocalDate.of(2026, 5, 20));
		assertThat(archived.totalIncome()).isEqualTo(500_000L);
		assertThat(archived.totalExpense()).isEqualTo(200_000L);
		assertThat(archived.balance()).isEqualTo(300_000L);
		assertThat(archived.entryCount()).isEqualTo(2);
		assertThat(archived.ledgerCount()).isEqualTo(1);

		// 원본은 비워진다 — 화면도 보관 직후 "새로운 폴더를 생성해주세요" 빈 화면이 된다.
		assertThat(ledgerRepository.findAllInGroup(groupId, null)).isEmpty();
		assertThat(entryRepository.findAllByGroupId(groupId)).isEmpty();
		assertThat(folderRepository.findAllByGroupId(groupId)).isEmpty();
	}

	@Test
	void 승인_대기_내역도_그대로_보관한다() {
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 500_000L, LocalDate.of(2026, 3, 1));
		// 일반 관리자가 등록하면 승인 대기로 남는다.
		createEntry(adminId, EntryType.EXPENSE, "승인 대기 지출", 100_000L, LocalDate.of(2026, 4, 1));

		Long archiveId = archiveService.create(groupId, ownerId, "보관").archiveId();
		var detail = archiveService.getDetail(archiveId, ownerId);

		assertThat(detail.summary().entryCount()).isEqualTo(2);
		assertThat(detail.ledgers()).singleElement().satisfies(ledger -> {
			assertThat(ledger.folderName()).isEqualTo("2025");
			assertThat(ledger.ledgerName()).isEqualTo("운영 장부");
			assertThat(ledger.budget()).isEqualTo(1_000_000L);
			assertThat(ledger.entries()).extracting(e -> e.approvalStatus())
					.containsExactly(ApprovalStatus.APPROVED, ApprovalStatus.PENDING);
			assertThat(ledger.entries()).extracting(e -> e.createdByName())
					.containsExactly("총무", "일반관리자");
		});
	}

	@Test
	void 보관해도_증빙_이미지는_남고_상세에서_볼_수_있다() {
		Long fileId = fileService.upload(ownerId,
				new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "image".getBytes()),
				FilePurpose.RECEIPT).fileId();
		entryService.create(ledgerId, ownerId, new EntryCreateRequest(EntryType.EXPENSE, "대관료",
				200_000L, LocalDate.of(2026, 5, 20), null, null, List.of(fileId)));

		Long archiveId = archiveService.create(groupId, ownerId, "보관").archiveId();

		var detail = archiveService.getDetail(archiveId, ownerId);
		assertThat(detail.ledgers()).singleElement().satisfies(ledger ->
				assertThat(ledger.entries()).singleElement().satisfies(entry ->
						assertThat(entry.receiptFiles()).singleElement()
								.satisfies(receipt -> assertThat(receipt.fileId()).isEqualTo(fileId))));
		assertThat(fileRepository.findById(fileId)).isPresent();
	}

	@Test
	void 보관된_증빙은_모임_관리자_누구나_열_수_있다() {
		Long fileId = fileService.upload(ownerId,
				new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "image".getBytes()),
				FilePurpose.RECEIPT).fileId();
		entryService.create(ledgerId, ownerId, new EntryCreateRequest(EntryType.EXPENSE, "대관료",
				200_000L, LocalDate.of(2026, 5, 20), null, null, List.of(fileId)));
		archiveService.create(groupId, ownerId, "보관");

		// 보관하면 entry 연결이 끊긴다. 올린 사람이 아니어도 같은 모임 관리자면 열려야 한다.
		assertThat(fileService.getAccessibleFile(fileId, adminId).getId()).isEqualTo(fileId);

		Long outsiderId = userRepository.save(User.create("out@example.com", "encoded", "남")).getId();
		assertThatThrownBy(() -> fileService.getAccessibleFile(fileId, outsiderId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 보관_기록을_지우면_그_안의_증빙도_사라진다() {
		Long fileId = fileService.upload(ownerId,
				new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "image".getBytes()),
				FilePurpose.RECEIPT).fileId();
		entryService.create(ledgerId, ownerId, new EntryCreateRequest(EntryType.EXPENSE, "대관료",
				200_000L, LocalDate.of(2026, 5, 20), null, null, List.of(fileId)));
		Long archiveId = archiveService.create(groupId, ownerId, "보관").archiveId();

		archiveService.delete(archiveId, ownerId);

		assertThat(fileRepository.findById(fileId)).isEmpty();
	}

	@Test
	void 마감된_회비가_들고_있던_내역_참조는_보관하며_끊는다() {
		Long memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, null, null)).memberId();
		Long duesId = duesService.create(groupId, ownerId, new DuesCreateRequest("2학기 회비", 30_000L,
				LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), List.of(memberId), ledgerId)).duesId();
		duesService.changePaymentStatus(duesId, memberId, ownerId, new PaymentStatusUpdateRequest("PAID"));
		duesService.close(duesId, ownerId);

		archiveService.create(groupId, ownerId, "보관");

		// 회비는 남지만, 사라진 내역을 가리키고 있으면 안 된다.
		assertThat(duesService.getDetail(duesId, ownerId).generatedEntryId()).isNull();
	}

	@Test
	void 진행_중인_회비가_있으면_보관할_수_없다() {
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 500_000L, LocalDate.of(2026, 3, 1));
		Long memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, null, null)).memberId();
		duesService.create(groupId, ownerId, new DuesCreateRequest("2학기 회비", 30_000L,
				LocalDate.now(), LocalDate.now().plusDays(30), List.of(memberId), ledgerId));

		assertThatThrownBy(() -> archiveService.create(groupId, ownerId, "보관"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ARCHIVE_BLOCKED_BY_OPEN_DUES);

		// 거부됐으면 원본이 그대로 있어야 한다.
		assertThat(entryRepository.findAllByGroupId(groupId)).hasSize(1);
	}

	@Test
	void 담을_내역이_없으면_보관하지_않는다() {
		assertThatThrownBy(() -> archiveService.create(groupId, ownerId, "빈 보관"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ARCHIVE_EMPTY);
	}

	@Test
	void 일반_관리자는_보관할_수_없다() {
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 500_000L, LocalDate.of(2026, 3, 1));

		assertThatThrownBy(() -> archiveService.create(groupId, adminId, "보관"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 보관_기록은_제목만_바꿀_수_있고_일반_관리자는_바꾸지_못한다() {
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 500_000L, LocalDate.of(2026, 3, 1));
		Long archiveId = archiveService.create(groupId, ownerId, "이전 제목").archiveId();

		var renamed = archiveService.rename(archiveId, ownerId, "2025 결산 기록");

		assertThat(renamed.title()).isEqualTo("2025 결산 기록");
		assertThat(renamed.entryCount()).isEqualTo(1);
		assertThatThrownBy(() -> archiveService.rename(archiveId, adminId, "일반관리자가 변경"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 보관함은_최신순이고_일반_관리자도_볼_수_있다() {
		createEntry(ownerId, EntryType.INCOME, "1차 수입", 100_000L, LocalDate.of(2026, 1, 1));
		archiveService.create(groupId, ownerId, "1차 보관");

		Long secondFolder = folderService.create(groupId, ownerId,
				new FolderCreateRequest("2026", null)).folderId();
		ledgerId = ledgerService.create(secondFolder, ownerId,
				new LedgerCreateRequest("새 장부", null)).ledgerId();
		createEntry(ownerId, EntryType.INCOME, "2차 수입", 200_000L, LocalDate.of(2026, 6, 1));
		archiveService.create(groupId, ownerId, "2차 보관");

		var archives = archiveService.getArchives(groupId, adminId);

		assertThat(archives).extracting(a -> a.title()).containsExactly("2차 보관", "1차 보관");
	}

	@Test
	void 보관_기록을_지우면_되돌릴_수_없다() {
		createEntry(ownerId, EntryType.INCOME, "회비 수입", 500_000L, LocalDate.of(2026, 3, 1));
		Long archiveId = archiveService.create(groupId, ownerId, "보관").archiveId();

		archiveService.delete(archiveId, ownerId);

		assertThat(archiveService.getArchives(groupId, ownerId)).isEmpty();
		assertThatThrownBy(() -> archiveService.getDetail(archiveId, ownerId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ARCHIVE_NOT_FOUND);
	}

	private void createEntry(Long userId, EntryType type, String title, long amount, LocalDate occurredOn) {
		entryService.create(ledgerId, userId,
				new EntryCreateRequest(type, title, amount, occurredOn, null, null, null));
	}
}
