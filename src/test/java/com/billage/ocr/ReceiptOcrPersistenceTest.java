package com.billage.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.billage.entry.EntryService;
import com.billage.entry.EntryType;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.entry.dto.EntryUpdateRequest;
import com.billage.file.FilePurpose;
import com.billage.file.FileService;
import com.billage.folder.FolderService;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.ocr.dto.ReceiptOcrResponse;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 인식 결과 보관(V22). 인식할 때 파일에 붙여 두고, 그 파일이 내역의 증빙이 되면 내역 상세에서
 * OCR 을 다시 부르지 않고 읽는다.
 */
class ReceiptOcrPersistenceTest extends IntegrationTest {

	/** 20x20 흰 PNG. 인식 전 크기 검증을 통과하는 최소한의 "진짜" 이미지다. */
	private static final byte[] VALID_PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAIAAAAC64paAAAALElEQVR4nGP8//8/A7mAiWydDKOaSQZMpGtB"
					+ "gFHNJAImUjUgg1HNJAKKAgwAnKADJa4TmNgAAAAASUVORK5CYII=");

	@Autowired
	ReceiptOcrService receiptOcrService;
	@Autowired
	ReceiptOcrRepository receiptOcrRepository;
	@Autowired
	FileService fileService;
	@Autowired
	EntryService entryService;
	@Autowired
	GroupService groupService;
	@Autowired
	FolderService folderService;
	@Autowired
	LedgerService ledgerService;
	@Autowired
	UserRepository userRepository;

	/** 호출 횟수를 세려고 가짜로 바꾼다 — 상세 조회가 OCR 을 다시 부르지 않는지가 이 기능의 핵심이다. */
	@MockitoBean
	ReceiptOcrClient ocrClient;

	private Long ownerId;
	private Long ledgerId;

	@BeforeEach
	void setUp() {
		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		Long groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
		Long folderId = folderService.create(groupId, ownerId, new FolderCreateRequest("2026", null)).folderId();
		ledgerId = ledgerService.create(folderId, ownerId, new LedgerCreateRequest("운영 장부", null)).ledgerId();

		when(ocrClient.recognize(any(), any())).thenReturn(result("빌리지마트", 12_500L));
	}

	@Test
	void 인식한_결과는_내역_상세에서_다시_읽힌다() {
		Long fileId = uploadReceipt();
		receiptOcrService.recognize(fileId, ownerId);
		Long entryId = createEntry(List.of(fileId));

		ReceiptOcrResponse ocr = entryService.getDetail(entryId, ownerId).ocr();

		assertThat(ocr.fileId()).isEqualTo(fileId);
		assertThat(ocr.merchantName()).isEqualTo("빌리지마트");
		assertThat(ocr.purchasedOn()).isEqualTo(LocalDate.of(2026, 10, 6));
		assertThat(ocr.totalAmount()).isEqualTo(12_500L);
		assertThat(ocr.items()).extracting(item -> item.name()).containsExactly("생수", "과자");
		assertThat(ocr.items().get(0).quantity()).isEqualTo(2);
		assertThat(ocr.items().get(1).unitPrice()).isNull();
		assertThat(ocr.recognizedAt()).isNotNull();
	}

	@Test
	void 내역_상세를_여러_번_열어도_OCR_을_다시_부르지_않는다() {
		Long fileId = uploadReceipt();
		receiptOcrService.recognize(fileId, ownerId);
		Long entryId = createEntry(List.of(fileId));

		entryService.getDetail(entryId, ownerId);
		entryService.getDetail(entryId, ownerId);

		verify(ocrClient, times(1)).recognize(any(), any());
	}

	@Test
	void 같은_파일을_다시_인식하면_덮어쓴다() {
		Long fileId = uploadReceipt();
		receiptOcrService.recognize(fileId, ownerId);
		when(ocrClient.recognize(any(), any())).thenReturn(result("다시읽은상점", 9_900L));

		receiptOcrService.recognize(fileId, ownerId);

		assertThat(receiptOcrRepository.count()).isEqualTo(1);
		Long entryId = createEntry(List.of(fileId));
		ReceiptOcrResponse ocr = entryService.getDetail(entryId, ownerId).ocr();
		assertThat(ocr.merchantName()).isEqualTo("다시읽은상점");
		assertThat(ocr.totalAmount()).isEqualTo(9_900L);
		assertThat(ocr.items()).hasSize(2);
	}

	@Test
	void 인식한_증빙이_없는_내역은_ocr_이_비어_있다() {
		Long fileId = uploadReceipt();
		Long entryId = createEntry(List.of(fileId));

		assertThat(entryService.getDetail(entryId, ownerId).ocr()).isNull();
		assertThat(entryService.getDetail(createEntry(null), ownerId).ocr()).isNull();
	}

	@Test
	void 증빙이_여러_장이면_가장_최근에_인식한_것을_보여_준다() {
		Long first = uploadReceipt();
		Long second = uploadReceipt();
		receiptOcrService.recognize(first, ownerId);
		when(ocrClient.recognize(any(), any())).thenReturn(result("나중상점", 3_000L));
		receiptOcrService.recognize(second, ownerId);

		Long entryId = createEntry(List.of(first, second));

		assertThat(entryService.getDetail(entryId, ownerId).ocr().fileId()).isEqualTo(second);
	}

	@Test
	void 증빙에서_빼면_파일과_함께_인식_결과도_사라진다() {
		Long fileId = uploadReceipt();
		receiptOcrService.recognize(fileId, ownerId);
		Long entryId = createEntry(List.of(fileId));

		// 증빙 전체 교체 — 빠진 파일은 저장소에서도 지워진다.
		entryService.update(entryId, ownerId, new EntryUpdateRequest(null, null, null, null, null, List.of()));

		assertThat(entryService.getDetail(entryId, ownerId).ocr()).isNull();
		assertThat(receiptOcrRepository.count()).isZero();
	}

	@Test
	void 내역을_지우면_인식_결과도_남지_않는다() {
		Long fileId = uploadReceipt();
		receiptOcrService.recognize(fileId, ownerId);
		Long entryId = createEntry(List.of(fileId));

		entryService.delete(entryId, ownerId);

		assertThat(receiptOcrRepository.count()).isZero();
	}

	private static ReceiptOcrResult result(String merchantName, long totalAmount) {
		return new ReceiptOcrResult(merchantName, LocalDate.of(2026, 10, 6), totalAmount, List.of(
				new ReceiptOcrItem("생수", 2, 1_200L, 2_400L, 0.96),
				new ReceiptOcrItem("과자", null, null, 4_500L, null)));
	}

	private Long uploadReceipt() {
		return fileService.upload(ownerId, new MockMultipartFile("file", "receipt.png", "image/png", VALID_PNG),
				FilePurpose.RECEIPT).fileId();
	}

	private Long createEntry(List<Long> receiptFileIds) {
		return entryService.create(ledgerId, ownerId, new EntryCreateRequest(EntryType.EXPENSE, "간식",
				12_500L, LocalDate.of(2026, 10, 6), null, null, receiptFileIds)).entryId();
	}
}
