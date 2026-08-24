package com.billage.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.file.FilePurpose;
import com.billage.file.FileRepository;
import com.billage.file.FileService;
import com.billage.file.FileStorage;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.group.dto.GroupUpdateRequest;
import com.billage.membership.GroupMembershipRepository;
import com.billage.membership.GroupMembershipService;
import com.billage.membership.dto.RoleUpdateRequest;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 모임 대표 이미지(더보기 > 모임 관리 > 모임 프로필 변경) 검증.
 * 교체 시 이전 파일이 남지 않는 것과, 미전달/null 전달을 구분하는 것이 핵심이다.
 */
class GroupImageServiceTest extends IntegrationTest {

	@Autowired
	GroupService groupService;
	@Autowired
	GroupMembershipService groupMembershipService;
	@Autowired
	FileService fileService;
	@Autowired
	FileRepository fileRepository;
	@Autowired
	FileStorage fileStorage;
	@Autowired
	GroupMembershipRepository groupMembershipRepository;
	@Autowired
	UserRepository userRepository;

	private Long ownerId;
	private Long adminId;
	private Long groupId;

	@BeforeEach
	void setUp() {
		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "encoded", "일반관리자")).getId();
		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
	}

	@Test
	void 모임_생성_시_대표_이미지를_지정할_수_있다() {
		Long fileId = uploadGroupImage(ownerId);

		var created = groupService.create(ownerId, new GroupCreateRequest("새모임", null, fileId));

		assertThat(created.groupImageUrl()).isEqualTo("/api/v1/files/" + fileId + "/content");
	}

	@Test
	void 이미지를_교체하면_이전_파일은_남지_않는다() {
		Long first = uploadGroupImage(ownerId);
		Long second = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(first)));

		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(second)));

		assertThat(fileRepository.findById(first)).isEmpty();
		assertThat(fileRepository.findById(second)).isPresent();
		assertThat(groupSpaceImageId()).isEqualTo(second);
	}

	@Test
	void null_을_보내면_기본_이미지로_되돌린다() {
		Long fileId = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));

		var updated = groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.empty()));

		assertThat(updated.groupImageUrl()).isNull();
		assertThat(groupSpaceImageId()).isNull();
		assertThat(fileRepository.findById(fileId)).isEmpty();
	}

	@Test
	void 이미지_필드를_빼고_보내면_그대로_둔다() {
		Long fileId = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));

		// 이름만 고치는 요청이 이미지를 날려서는 안 된다.
		var updated = groupService.update(groupId, ownerId, new GroupUpdateRequest("이름만변경", null, null));

		assertThat(updated.name()).isEqualTo("이름만변경");
		assertThat(groupSpaceImageId()).isEqualTo(fileId);
		assertThat(fileRepository.findById(fileId)).isPresent();
	}

	@Test
	void 증빙_용도_파일은_대표_이미지로_쓸_수_없다() {
		MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "bytes".getBytes());
		Long receiptId = fileService.upload(ownerId, file, FilePurpose.RECEIPT).fileId();

		assertThatThrownBy(() -> groupService.update(groupId, ownerId,
				new GroupUpdateRequest(null, null, Optional.of(receiptId))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_FILE_PURPOSE);
	}

	@Test
	void 남이_올린_파일은_대표_이미지로_쓸_수_없다() {
		Long othersFileId = uploadGroupImage(adminId);

		assertThatThrownBy(() -> groupService.update(groupId, ownerId,
				new GroupUpdateRequest(null, null, Optional.of(othersFileId))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 일반_관리자는_대표_이미지를_바꿀_수_없다() {
		joinAsAdmin();
		Long fileId = uploadGroupImage(adminId);

		assertThatThrownBy(() -> groupService.update(groupId, adminId,
				new GroupUpdateRequest(null, null, Optional.of(fileId))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 대표_이미지는_모임_관리자_전원이_열람할_수_있다() {
		joinAsAdmin();
		Long fileId = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));

		// 업로더가 아닌 일반 관리자도 볼 수 있어야 목록 화면이 깨지지 않는다.
		assertThat(fileService.getAccessibleFile(fileId, adminId).getId()).isEqualTo(fileId);
	}

	@Test
	void 대표_이미지로_쓰이는_파일은_직접_삭제할_수_없다() {
		Long fileId = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));

		// 지워지면 모임에 깨진 URL 만 남는다.
		assertThatThrownBy(() -> fileService.delete(fileId, ownerId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_IN_USE);
	}

	@Test
	void 한_파일을_두_모임이_나눠_쓸_수_없다() {
		Long fileId = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));
		Long otherGroupId = groupService.create(ownerId, new GroupCreateRequest("다른모임", null, null)).groupId();

		assertThatThrownBy(() -> groupService.update(otherGroupId, ownerId,
				new GroupUpdateRequest(null, null, Optional.of(fileId))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_IN_USE);
	}

	@Test
	void 이미_다른_모임이_쓰는_파일로는_모임을_만들_수_없다() {
		Long fileId = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));

		assertThatThrownBy(() -> groupService.create(ownerId, new GroupCreateRequest("새모임", null, fileId)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FILE_IN_USE);
	}

	@Test
	void 이미_증빙으로_쓰인_파일도_대표_이미지가_될_수_없다() {
		// 용도가 GROUP_IMAGE 여도 이미 임자가 있으면 못 쓴다.
		Long fileId = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));
		Long otherGroupId = groupService.create(adminId, new GroupCreateRequest("남의모임", null, null)).groupId();

		assertThatThrownBy(() -> groupService.update(otherGroupId, adminId,
				new GroupUpdateRequest(null, null, Optional.of(fileId))))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void 이미지를_떼어낸_파일은_다시_쓸_수_있다() {
		Long fileId = uploadGroupImage(ownerId);
		Long keeper = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));

		// 교체하면 이전 파일은 삭제되므로, 새로 올린 파일이 자리를 이어받는다.
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(keeper)));

		assertThat(groupSpaceImageId()).isEqualTo(keeper);
		assertThat(fileRepository.findById(fileId)).isEmpty();
	}

	@Test
	void 모임을_지우면_대표_이미지도_지워진다() {
		Long fileId = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));

		groupService.delete(groupId, ownerId);

		assertThat(fileRepository.findById(fileId)).isEmpty();
	}

	@Test
	void 목록과_상세에도_이미지_URL_이_나간다() {
		Long fileId = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));
		String expected = "/api/v1/files/" + fileId + "/content";

		assertThat(groupService.getDetail(groupId, ownerId).groupImageUrl()).isEqualTo(expected);
		assertThat(groupService.getMyGroups(ownerId)).singleElement()
				.satisfies(group -> assertThat(group.groupImageUrl()).isEqualTo(expected));
	}

	@Test
	void 같은_이미지를_다시_보내도_그대로_남는다() {
		// 화면이 현재 값을 그대로 다시 보내는 흔한 경우. 떼었다 지우면 이미지가 사라진다.
		Long fileId = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));

		var updated = groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));

		assertThat(updated.groupImageUrl()).isEqualTo("/api/v1/files/" + fileId + "/content");
		assertThat(fileRepository.findById(fileId)).isPresent();
	}

	@Test
	void 공동_총무가_남이_올린_현재_이미지를_그대로_다시_보내도_통과한다() {
		joinAsAdmin();
		Long coOwnerMembershipId = groupMembershipRepository.findByGroupIdAndUserId(groupId, adminId)
				.orElseThrow().getId();
		groupMembershipService.changeRole(groupId, ownerId, coOwnerMembershipId, new RoleUpdateRequest("OWNER"));
		Long fileId = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(fileId)));

		// 이름만 바꾸면서 이미지 값을 그대로 실어 보내는 흔한 폼 저장. 업로더가 아니어도 막히면 안 된다.
		var updated = groupService.update(groupId, adminId,
				new GroupUpdateRequest("이름변경", null, Optional.of(fileId)));

		assertThat(updated.groupImageUrl()).isEqualTo("/api/v1/files/" + fileId + "/content");
	}

	@Test
	void 교체에_실패하면_기존_이미지가_그대로_남는다() {
		Long current = uploadGroupImage(ownerId);
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, null, Optional.of(current)));
		Long othersFile = uploadGroupImage(adminId);

		assertThatThrownBy(() -> groupService.update(groupId, ownerId,
				new GroupUpdateRequest(null, null, Optional.of(othersFile))))
				.isInstanceOf(BusinessException.class);

		// 저장소 삭제는 롤백되지 않으므로, 실패 경로에서 기존 파일을 건드리면 안 된다.
		assertThat(groupSpaceImageId()).isEqualTo(current);
		assertThat(fileRepository.findById(current)).isPresent();
		assertThat(fileStorage.load(fileRepository.findById(current).orElseThrow().getStorageKey()).exists()).isTrue();
	}

	@Test
	void 직접_삭제와_이미지_지정이_동시에_들어와도_깨진_참조가_남지_않는다() throws Exception {
		Long fileId = uploadGroupImage(ownerId);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			pool.submit(() -> attempt(start, () -> groupService.update(groupId, ownerId,
					new GroupUpdateRequest(null, null, Optional.of(fileId)))));
			pool.submit(() -> attempt(start, () -> fileService.delete(fileId, ownerId)));
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		} finally {
			pool.shutdownNow();
		}

		// 모임이 파일을 가리킨다면 그 파일은 반드시 남아 있어야 한다.
		Long referenced = groupSpaceImageId();
		if (referenced != null) {
			assertThat(fileRepository.findById(referenced)).isPresent();
		}
	}

	private void attempt(CountDownLatch start, Runnable action) {
		try {
			start.await();
			action.run();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (RuntimeException e) {
			// 뒤늦게 들어온 쪽은 FILE_IN_USE 또는 FILE_NOT_FOUND 로 막힌다. 둘 다 정상이다.
		}
	}

	private Long uploadGroupImage(Long userId) {
		MockMultipartFile file = new MockMultipartFile("file", "group.jpg", "image/jpeg", "bytes".getBytes());
		return fileService.upload(userId, file, FilePurpose.GROUP_IMAGE).fileId();
	}

	private void joinAsAdmin() {
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(adminId, code);
	}

	private Long groupSpaceImageId() {
		return fileRepository.findGroupImage(groupId).map(file -> file.getId()).orElse(null);
	}
}
