package com.billage.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.file.FilePurpose;
import com.billage.file.FileRepository;
import com.billage.file.FileService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.group.dto.GroupUpdateRequest;
import com.billage.membership.GroupMembershipService;
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
	GroupSpaceRepository groupSpaceRepository;
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

	private Long uploadGroupImage(Long userId) {
		MockMultipartFile file = new MockMultipartFile("file", "group.jpg", "image/jpeg", "bytes".getBytes());
		return fileService.upload(userId, file, FilePurpose.GROUP_IMAGE).fileId();
	}

	private void joinAsAdmin() {
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(adminId, code);
	}

	private Long groupSpaceImageId() {
		return groupSpaceRepository.findById(groupId).orElseThrow().getGroupImageFileId();
	}
}
