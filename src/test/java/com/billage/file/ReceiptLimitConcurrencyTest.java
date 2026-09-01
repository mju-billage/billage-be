package com.billage.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.billage.entry.Entry;
import com.billage.entry.EntryRepository;
import com.billage.entry.EntryType;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.entry.EntryService;
import com.billage.folder.FolderService;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 증빙은 내역당 10장까지다. 세는 것과 붙이는 것이 원자적이지 않으면 동시 요청이 서로의 증빙을
 * 세지 못해 상한을 넘긴다.
 */
class ReceiptLimitConcurrencyTest extends IntegrationTest {

	@Autowired
	FileService fileService;
	@Autowired
	FileRepository fileRepository;
	@Autowired
	EntryService entryService;
	@Autowired
	EntryRepository entryRepository;
	@Autowired
	LedgerService ledgerService;
	@Autowired
	FolderService folderService;
	@Autowired
	GroupService groupService;
	@Autowired
	UserRepository userRepository;

	@Test
	void 동시에_붙여도_증빙은_열_장을_넘지_않는다() throws Exception {
		Long ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		Long groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
		Long folderId = folderService.create(groupId, ownerId, new FolderCreateRequest("폴더", null)).folderId();
		Long ledgerId = ledgerService.create(folderId, ownerId, new LedgerCreateRequest("장부", null)).ledgerId();
		Long entryId = entryService.create(ledgerId, ownerId, new EntryCreateRequest(EntryType.EXPENSE,
				"대관료", 1000L, LocalDate.now(), null, null, null)).entryId();
		Entry entry = entryRepository.findById(entryId).orElseThrow();

		List<Long> first = upload(ownerId, 1, 6);
		List<Long> second = upload(ownerId, 7, 12);

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			pool.submit(() -> link(start, entry, first, ownerId));
			pool.submit(() -> link(start, entry, second, ownerId));
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		} finally {
			pool.shutdownNow();
		}

		// 6 + 6 = 12 이므로 한쪽만 성공해야 한다.
		assertThat(fileRepository.findByEntryId(entryId)).hasSize(6);
	}

	private List<Long> upload(Long userId, int from, int to) {
		return IntStream.rangeClosed(from, to)
				.mapToObj(i -> fileService.upload(userId,
						new MockMultipartFile("file", "receipt" + i + ".jpg", "image/jpeg", "image".getBytes()),
						FilePurpose.RECEIPT).fileId())
				.toList();
	}

	private void link(CountDownLatch start, Entry entry, List<Long> fileIds, Long userId) {
		try {
			start.await();
			fileService.linkReceipts(entry, fileIds, userId);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (RuntimeException e) {
			// 뒤늦게 들어온 쪽은 상한에 걸려 실패한다. 정상이다.
		}
	}
}
