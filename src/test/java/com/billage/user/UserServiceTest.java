package com.billage.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.billage.auth.social.SocialAccount;
import com.billage.auth.social.SocialAccountRepository;
import com.billage.auth.social.SocialProvider;
import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.file.FilePurpose;
import com.billage.file.FileRepository;
import com.billage.file.FileService;
import com.billage.file.FileStorage;
import com.billage.group.GroupService;
import com.billage.group.GroupSpaceRepository;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.membership.GroupMembershipRepository;
import com.billage.membership.GroupMembershipService;
import com.billage.membership.GroupRole;
import com.billage.support.IntegrationTest;
import com.billage.user.dto.PasswordChangeRequest;
import com.billage.user.dto.ProfileUpdateRequest;
import com.billage.user.dto.WithdrawRequest;
import com.billage.user.dto.WithdrawRequest.OwnershipTransfer;

/**
 * 마이페이지(내 프로필·비밀번호 변경·회원 탈퇴) 검증.
 *
 * <p>탈퇴 쪽 관심사는 두 가지다 — 총무 없는 모임이 남지 않는 것과, 계정이 사라져도 모임·증빙 같은
 * 회계 이력이 함께 사라지지 않는 것.
 */
class UserServiceTest extends IntegrationTest {

	private static final String PASSWORD = "Password123!";
	private static final String NEW_PASSWORD = "NewPassword456!";

	@Autowired
	UserService userService;
	@Autowired
	UserRepository userRepository;
	@Autowired
	GroupService groupService;
	@Autowired
	GroupSpaceRepository groupSpaceRepository;
	@Autowired
	GroupMembershipService groupMembershipService;
	@Autowired
	GroupMembershipRepository groupMembershipRepository;
	@Autowired
	FileService fileService;
	@Autowired
	FileRepository fileRepository;
	@Autowired
	FileStorage fileStorage;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	WithdrawalReasonRepository withdrawalReasonRepository;
	@Autowired
	SocialAccountRepository socialAccountRepository;

	private Long userId;
	private Long otherId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(User.create("me@example.com", passwordEncoder.encode(PASSWORD), "홍길동")).getId();
		otherId = userRepository.save(User.create("other@example.com", passwordEncoder.encode(PASSWORD), "김철수"))
				.getId();
	}

	// --- 내 프로필 ---

	@Test
	void 내_프로필을_조회한다() {
		var profile = userService.getMyProfile(userId);

		assertThat(profile.email()).isEqualTo("me@example.com");
		assertThat(profile.name()).isEqualTo("홍길동");
		assertThat(profile.profileImageUrl()).isNull();
		assertThat(profile.createdAt()).isNotNull();
	}

	@Test
	void 소셜_계정은_loginProvider_로_연결된_수단을_돌려준다() {
		User socialUser = userRepository.save(User.createSocial("social@example.com", "소셜", null));
		socialAccountRepository.save(
				SocialAccount.link(socialUser, SocialProvider.GOOGLE, "google-1", "social@example.com"));

		assertThat(userService.getMyProfile(socialUser.getId()).loginProvider()).isEqualTo("GOOGLE");
	}

	@Test
	void 이름을_수정한다() {
		var updated = userService.updateMyProfile(userId, new ProfileUpdateRequest("김빌리지", null));

		assertThat(updated.name()).isEqualTo("김빌리지");
		assertThat(userRepository.findById(userId).orElseThrow().getName()).isEqualTo("김빌리지");
	}

	@Test
	void 공백_이름은_거부한다() {
		assertThatThrownBy(() -> userService.updateMyProfile(userId, new ProfileUpdateRequest("   ", null)))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void 프로필_이미지를_지정한다() {
		Long fileId = uploadProfileImage(userId);

		var updated = userService.updateMyProfile(userId, new ProfileUpdateRequest(null, Optional.of(fileId)));

		assertThat(updated.profileImageUrl()).isEqualTo("/api/v1/files/" + fileId + "/content");
		assertThat(fileRepository.findProfileImage(userId)).isPresent();
	}

	@Test
	void 프로필_이미지를_교체하면_이전_파일은_남지_않는다() {
		Long first = uploadProfileImage(userId);
		userService.updateMyProfile(userId, new ProfileUpdateRequest(null, Optional.of(first)));
		String firstKey = fileRepository.findById(first).orElseThrow().getStorageKey();

		Long second = uploadProfileImage(userId);
		userService.updateMyProfile(userId, new ProfileUpdateRequest(null, Optional.of(second)));

		assertThat(fileRepository.findById(first)).isEmpty();
		// 저장소에서도 사라진다 — 없는 키를 읽으면 FILE_NOT_FOUND 다.
		assertThatThrownBy(() -> fileStorage.load(firstKey)).isInstanceOf(BusinessException.class);
		assertThat(fileRepository.findProfileImage(userId).orElseThrow().getId()).isEqualTo(second);
	}

	@Test
	void null_을_보내면_기본_아바타로_되돌린다() {
		Long fileId = uploadProfileImage(userId);
		userService.updateMyProfile(userId, new ProfileUpdateRequest(null, Optional.of(fileId)));

		var updated = userService.updateMyProfile(userId, new ProfileUpdateRequest(null, Optional.empty()));

		assertThat(updated.profileImageUrl()).isNull();
		assertThat(fileRepository.findById(fileId)).isEmpty();
	}

	@Test
	void 이미지_필드를_아예_보내지_않으면_그대로_둔다() {
		Long fileId = uploadProfileImage(userId);
		userService.updateMyProfile(userId, new ProfileUpdateRequest(null, Optional.of(fileId)));

		var updated = userService.updateMyProfile(userId, new ProfileUpdateRequest("새이름", null));

		assertThat(updated.profileImageUrl()).isEqualTo("/api/v1/files/" + fileId + "/content");
	}

	@Test
	void 남의_프로필_이미지는_열_수_없다() {
		Long fileId = uploadProfileImage(userId);
		userService.updateMyProfile(userId, new ProfileUpdateRequest(null, Optional.of(fileId)));

		assertThatThrownBy(() -> fileService.getAccessibleFile(fileId, otherId))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining(ErrorCode.ACCESS_DENIED.getMessage());
	}

	// --- 비밀번호 변경 ---

	@Test
	void 비밀번호를_변경한다() {
		userService.changePassword(userId, new PasswordChangeRequest(PASSWORD, NEW_PASSWORD, null));

		String encoded = userRepository.findById(userId).orElseThrow().getPassword();
		assertThat(passwordEncoder.matches(NEW_PASSWORD, encoded)).isTrue();
	}

	@Test
	void 현재_비밀번호가_틀리면_거부한다() {
		assertThatThrownBy(() -> userService.changePassword(userId,
				new PasswordChangeRequest("WrongPassword1!", NEW_PASSWORD, null)))
				.isInstanceOf(BusinessException.class);

		assertThat(passwordEncoder.matches(PASSWORD, userRepository.findById(userId).orElseThrow().getPassword()))
				.isTrue();
	}

	@Test
	void 소셜_전용_계정은_비밀번호를_바꿀_수_없다() {
		Long socialUserId = userRepository.save(User.createSocial("social@example.com", "소셜", null)).getId();

		assertThatThrownBy(() -> userService.changePassword(socialUserId,
				new PasswordChangeRequest(PASSWORD, NEW_PASSWORD, null)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining(ErrorCode.PASSWORD_CHANGE_NOT_ALLOWED.getMessage());
	}

	// --- 회원 탈퇴 ---

	@Test
	void 나만_있는_모임은_탈퇴와_함께_삭제된다() {
		Long groupId = createGroup(userId);

		userService.withdraw(userId, withdrawRequest(List.of()));

		assertThat(groupSpaceRepository.findById(groupId)).isEmpty();
		assertThat(userRepository.findById(userId)).isEmpty();
	}

	@Test
	void 유일한_총무면_권한을_넘기지_않고는_탈퇴할_수_없다() {
		Long groupId = createGroup(userId);
		joinAsAdmin(groupId, otherId);

		assertThatThrownBy(() -> userService.withdraw(userId, withdrawRequest(List.of())))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining(ErrorCode.OWNER_TRANSFER_REQUIRED.getMessage());

		assertThat(userRepository.findById(userId)).isPresent();
		assertThat(groupSpaceRepository.findById(groupId)).isPresent();
	}

	@Test
	void 권한을_넘기면_상대가_총무가_되고_모임은_남는다() {
		Long groupId = createGroup(userId);
		joinAsAdmin(groupId, otherId);

		userService.withdraw(userId, withdrawRequest(List.of(new OwnershipTransfer(groupId, otherId))));

		assertThat(groupSpaceRepository.findById(groupId)).isPresent();
		assertThat(groupMembershipRepository.findByGroupIdAndUserId(groupId, otherId).orElseThrow().getRole())
				.isEqualTo(GroupRole.OWNER);
		assertThat(groupMembershipRepository.findByGroupIdAndUserId(groupId, userId)).isEmpty();
		assertThat(userRepository.findById(userId)).isEmpty();
	}

	@Test
	void 공동_총무가_있으면_권한_이전_없이_탈퇴한다() {
		Long groupId = createGroup(userId);
		joinAsAdmin(groupId, otherId);
		promoteToOwner(groupId, otherId);

		userService.withdraw(userId, withdrawRequest(List.of()));

		assertThat(groupSpaceRepository.findById(groupId)).isPresent();
		assertThat(userRepository.findById(userId)).isEmpty();
	}

	@Test
	void 일반_관리자로_속한_모임은_관계만_끊는다() {
		Long groupId = createGroup(otherId);
		joinAsAdmin(groupId, userId);

		userService.withdraw(userId, withdrawRequest(List.of()));

		assertThat(groupSpaceRepository.findById(groupId)).isPresent();
		assertThat(groupMembershipRepository.findByGroupIdAndUserId(groupId, userId)).isEmpty();
	}

	@Test
	void 탈퇴해도_남은_모임의_대표_이미지는_사라지지_않는다() {
		Long groupId = createGroup(userId);
		joinAsAdmin(groupId, otherId);
		Long fileId = uploadGroupImage(userId);
		groupService.update(groupId, userId,
				new com.billage.group.dto.GroupUpdateRequest(null, null, Optional.of(fileId)));

		userService.withdraw(userId, withdrawRequest(List.of(new OwnershipTransfer(groupId, otherId))));

		var file = fileRepository.findById(fileId).orElseThrow();
		assertThat(file.getGroupId()).isEqualTo(groupId);
		assertThat(file.getUploadedBy()).isNull();
		assertThat(groupSpaceRepository.findById(groupId).orElseThrow().getCreatedBy()).isNull();
	}

	@Test
	void 올려두고_붙이지_않은_파일은_탈퇴와_함께_지운다() {
		// 업로더 표시만 비우면 아무도 열지도 지우지도 못하는 고아가 저장소에 남는다.
		Long fileId = uploadReceipt(userId);
		String key = fileRepository.findById(fileId).orElseThrow().getStorageKey();

		userService.withdraw(userId, withdrawRequest(List.of()));

		assertThat(fileRepository.findById(fileId)).isEmpty();
		assertThatThrownBy(() -> fileStorage.load(key)).isInstanceOf(BusinessException.class);
	}

	@Test
	void 탈퇴_사유는_계정과_따로_남는다() {
		userService.withdraw(userId, new WithdrawRequest(List.of(),
				List.of(WithdrawalReasonType.USAGE_UNCLEAR, WithdrawalReasonType.ETC), "다시 가입할게요"));

		var saved = withdrawalReasonRepository.findAll();
		assertThat(saved).hasSize(2);
		assertThat(saved).anyMatch(reason -> reason.getReason() == WithdrawalReasonType.ETC
				&& "다시 가입할게요".equals(reason.getDetail()));
	}

	@Test
	void 기타_사유는_상세_입력이_필요하다() {
		assertThatThrownBy(() -> userService.withdraw(userId,
				new WithdrawRequest(List.of(), List.of(WithdrawalReasonType.ETC), "  ")))
				.isInstanceOf(BusinessException.class);

		assertThat(userRepository.findById(userId)).isPresent();
	}

	@Test
	void 넘길_필요가_없는_모임을_지정하면_거부한다() {
		Long groupId = createGroup(otherId);
		joinAsAdmin(groupId, userId);

		assertThatThrownBy(() -> userService.withdraw(userId,
				withdrawRequest(List.of(new OwnershipTransfer(groupId, otherId)))))
				.isInstanceOf(BusinessException.class);
	}

	/** 서비스 밖에서 바꾸므로 직접 저장해야 반영된다. */
	private void promoteToOwner(Long groupId, Long targetUserId) {
		var membership = groupMembershipRepository.findByGroupIdAndUserId(groupId, targetUserId).orElseThrow();
		membership.changeRole(GroupRole.OWNER);
		groupMembershipRepository.save(membership);
	}

	private WithdrawRequest withdrawRequest(List<OwnershipTransfer> transfers) {
		return new WithdrawRequest(transfers, List.of(WithdrawalReasonType.NO_LONGER_NEEDED), null);
	}

	private Long createGroup(Long ownerId) {
		return groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
	}

	private void joinAsAdmin(Long groupId, Long joinerId) {
		String code = groupMembershipService.createInvitation(groupId, ownerOf(groupId)).invitationCode();
		groupMembershipService.join(joinerId, code);
	}

	private Long ownerOf(Long groupId) {
		return groupMembershipRepository.findByGroupId(groupId).stream()
				.filter(membership -> membership.getRole() == GroupRole.OWNER)
				.findFirst().orElseThrow().getUserId();
	}

	private Long uploadProfileImage(Long uploaderId) {
		MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", "bytes".getBytes());
		return fileService.upload(uploaderId, file, FilePurpose.PROFILE_IMAGE).fileId();
	}

	private Long uploadReceipt(Long uploaderId) {
		MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "bytes".getBytes());
		return fileService.upload(uploaderId, file, FilePurpose.RECEIPT).fileId();
	}

	private Long uploadGroupImage(Long uploaderId) {
		MockMultipartFile file = new MockMultipartFile("file", "group.jpg", "image/jpeg", "bytes".getBytes());
		return fileService.upload(uploaderId, file, FilePurpose.GROUP_IMAGE).fileId();
	}
}
