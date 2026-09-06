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
import com.billage.entry.EntryService;
import com.billage.entry.dto.EntryCreateRequest;
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
 * 탈퇴는 "아직 안 쓰이는 업로드"를 지운다. 세는 것과 지우는 것이 원자적이지 않으면,
 * 그 사이에 다른 기기가 같은 파일을 내역에 붙였을 때 사용 중인 증빙이 사라져 참조가 깨진다.
 *
 * <p>어느 쪽이 이겨도 상관없다. 붙는 데 성공했으면 파일이 남아 있어야 하고,
 * 지우는 데 성공했으면 그 내역에 증빙이 없어야 한다 — 둘 다 아닌 상태가 나오면 안 된다.
 */
class UploaderClearConcurrencyTest extends IntegrationTest {

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
	void 붙이는_중에_탈퇴해도_내역이_잃어버린_증빙을_가리키지_않는다() throws Exception {
		Long ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		Long groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
		Long folderId = folderService.create(groupId, ownerId, new FolderCreateRequest("폴더", null)).folderId();
		Long ledgerId = ledgerService.create(folderId, ownerId, new LedgerCreateRequest("장부", null)).ledgerId();
		Long entryId = entryService.create(ledgerId, ownerId, new EntryCreateRequest(EntryType.EXPENSE,
				"대관료", 1000L, LocalDate.now(), null, null, null)).entryId();
		Entry entry = entryRepository.findById(entryId).orElseThrow();

		List<Long> fileIds = IntStream.rangeClosed(1, 5)
				.mapToObj(i -> fileService.upload(ownerId,
						new MockMultipartFile("file", "receipt" + i + ".jpg", "image/jpeg", "image".getBytes()),
						FilePurpose.RECEIPT).fileId())
				.toList();

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			pool.submit(() -> run(start, () -> fileService.linkReceipts(entry, fileIds, ownerId)));
			pool.submit(() -> run(start, () -> fileService.clearUploader(ownerId)));
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		} finally {
			pool.shutdownNow();
		}

		// 내역에 남은 증빙은 전부 실제로 존재해야 한다 — 지워진 파일을 가리키고 있으면 안 된다.
		List<UploadedFile> linked = fileRepository.findByEntryId(entryId);
		assertThat(linked).allSatisfy(file -> assertThat(fileRepository.findById(file.getId())).isPresent());
		assertThat(linked.size()).isIn(0, fileIds.size());
	}

	private void run(CountDownLatch start, Runnable task) {
		try {
			start.await();
			task.run();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (RuntimeException e) {
			// 진 쪽은 실패한다(파일이 이미 사라졌거나, 이미 임자가 생겼다). 정상이다.
		}
	}
}
