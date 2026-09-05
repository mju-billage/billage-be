package com.billage.user;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.auth.email.EmailVerificationRepository;
import com.billage.auth.social.SocialAccount;
import com.billage.auth.social.SocialAccountRepository;
import com.billage.auth.token.RefreshToken;
import com.billage.auth.token.RefreshTokenRepository;
import com.billage.auth.token.TokenHasher;
import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.file.FileService;
import com.billage.file.UploadedFile;
import com.billage.group.GroupService;
import com.billage.group.GroupSpaceRepository;
import com.billage.membership.GroupInvitationRepository;
import com.billage.membership.GroupMembership;
import com.billage.membership.GroupMembershipRepository;
import com.billage.membership.GroupRole;
import com.billage.user.dto.MyProfileResponse;
import com.billage.user.dto.PasswordChangeRequest;
import com.billage.user.dto.ProfileUpdateRequest;
import com.billage.user.dto.WithdrawRequest;
import com.billage.user.dto.WithdrawRequest.OwnershipTransfer;

import lombok.RequiredArgsConstructor;

/**
 * 내 계정 관리 — 프로필 조회·수정, 비밀번호 변경, 회원 탈퇴(「더보기 &gt; 설정」).
 *
 * <p>모임 리소스가 아니라 본인 계정만 다루므로 소유권 검증({@code GroupAccessGuard})이 필요한 자리는
 * 탈퇴의 권한 이전뿐이다 — 거기서는 내가 그 모임의 총무인지를 직접 확인한다.
 */
@Service
@RequiredArgsConstructor
public class UserService {

	private static final String EMAIL_PROVIDER = "EMAIL";

	private final UserRepository userRepository;
	private final FileService fileService;
	private final PasswordEncoder passwordEncoder;
	private final SocialAccountRepository socialAccountRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final TokenHasher tokenHasher;
	private final GroupMembershipRepository groupMembershipRepository;
	private final GroupSpaceRepository groupSpaceRepository;
	private final GroupInvitationRepository groupInvitationRepository;
	private final GroupService groupService;
	private final EmailVerificationRepository emailVerificationRepository;
	private final WithdrawalReasonRepository withdrawalReasonRepository;

	@Transactional(readOnly = true)
	public MyProfileResponse getMyProfile(Long userId) {
		User user = requireUser(userId);
		return MyProfileResponse.of(user, fileService.profileImageUrl(userId), loginProviderOf(user));
	}

	/**
	 * 내 정보 수정(부분 수정). 전달되지 않은 필드는 그대로 둔다.
	 * 공백 전용 이름은 {@code @Size(min = 1)} 을 통과해 trim 후 빈 이름이 되므로 여기서 막는다.
	 */
	@Transactional
	public MyProfileResponse updateMyProfile(Long userId, ProfileUpdateRequest request) {
		User user = requireUser(userId);
		if (request.name() != null) {
			String name = request.name().trim();
			if (name.isEmpty()) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST, "이름은 공백일 수 없습니다.");
			}
			user.changeName(name);
		}
		if (request.imageChangeRequested()) {
			replaceProfileImage(userId, request.targetImageFileId());
		}

		return MyProfileResponse.of(user, fileService.profileImageUrl(userId), loginProviderOf(user));
	}

	/**
	 * 프로필 이미지 교체. 새 파일이 null 이면 기본 아바타로 되돌린다.
	 * 떼기 → 선점 → 이전 파일 삭제 순서와 그 이유는 모임 대표 이미지 교체와 같다
	 * (저장소 삭제는 롤백되지 않으므로 선점이 확정된 뒤에 지운다).
	 */
	private void replaceProfileImage(Long userId, Long newFileId) {
		if (fileService.findProfileImage(userId).map(file -> file.getId().equals(newFileId)).orElse(false)) {
			return;
		}
		Optional<UploadedFile> previous = fileService.detachProfileImage(userId);
		if (newFileId != null) {
			fileService.claimProfileImage(newFileId, userId);
		}
		previous.ifPresent(fileService::deleteDetached);
	}

	/**
	 * 비밀번호 변경. 성공하면 지금 쓰고 있는 기기를 뺀 나머지 세션을 끊는다 —
	 * 유출을 의심해 바꾸는 경우가 많은데 남은 Refresh Token 을 그대로 두면 변경이 무의미해진다.
	 */
	@Transactional
	public void changePassword(Long userId, PasswordChangeRequest request) {
		User user = requireUser(userId);
		if (!user.hasPassword()) {
			// 화면은 소셜 계정에 이 메뉴를 아예 숨기지만, 서버도 막는다.
			throw new BusinessException(ErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);
		}
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "현재 비밀번호가 일치하지 않습니다.");
		}
		user.changePassword(passwordEncoder.encode(request.newPassword()));

		refreshTokenRepository.revokeActiveForUser(userId, currentFamilyId(request.refreshToken()),
				LocalDateTime.now());
	}

	/**
	 * 지금 요청을 보낸 기기의 토큰 패밀리. 클라이언트가 Refresh Token 을 함께 주지 않으면 알 수 없으므로
	 * null 을 돌려주고, 그 경우 모든 기기가 끊긴다.
	 */
	private String currentFamilyId(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			return null;
		}
		return refreshTokenRepository.findFirstByTokenHash(tokenHasher.hash(rawRefreshToken))
				.map(RefreshToken::getFamilyId)
				.orElse(null);
	}

	/**
	 * 회원 탈퇴. 권한 이전과 계정 삭제를 한 트랜잭션으로 처리한다(화면도 마지막 확인에서 일괄 처리라고 못 박는다).
	 *
	 * <p>내가 유일한 총무인 모임은 두 갈래다 — 다른 관리자가 있으면 그중 한 명에게 넘겨야 하고(없으면 409),
	 * 나 말고 관리자가 아무도 없으면 넘길 곳이 없어 모임째 삭제한다.
	 *
	 * <p>계정 행은 실제로 지우지만 회계 이력은 남는다. 내역의 작성자·승인자는 이름을 그 시점 값으로
	 * 스냅샷해 두었고, 모임·초대 코드·증빙에 남은 "누가 만들었나" 표시만 비운다.
	 */
	@Transactional
	public void withdraw(Long userId, WithdrawRequest request) {
		// 계정 행을 먼저 잠근다 — 파일 정리와 계정 삭제 사이에 새 업로드가 끼어들지 못하게 한다.
		// 자세한 이유는 UserRepository#findByIdForUpdate.
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		String email = user.getEmail();

		List<GroupMembership> memberships = groupMembershipRepository.findByUserId(userId);
		List<Long> needsTransfer = new ArrayList<>();
		List<Long> groupsToDelete = new ArrayList<>();
		for (GroupMembership membership : memberships) {
			if (!membership.isOwner()) {
				continue;
			}
			Long groupId = membership.getGroup().getId();
			// 총무 행을 잠근 채로 센다. 일반 count 로는 공동 총무가 동시에 빠져나가는 경우를 막지 못한다.
			if (groupMembershipRepository.lockOwners(groupId).size() > 1) {
				continue;
			}
			if (hasOtherMembership(groupId, userId)) {
				needsTransfer.add(groupId);
			} else {
				groupsToDelete.add(groupId);
			}
		}

		Map<Long, Long> transfers = validateTransfers(request.transfers(), needsTransfer, userId);
		saveReasons(request);

		for (Map.Entry<Long, Long> transfer : transfers.entrySet()) {
			promoteToOwner(transfer.getKey(), transfer.getValue());
		}
		// 넘길 곳이 있는 모임은 관리자 관계만 끊고, 나 혼자 남은 모임은 통째로 지운다.
		memberships.stream()
				.filter(membership -> !groupsToDelete.contains(membership.getGroup().getId()))
				.forEach(groupMembershipRepository::delete);
		groupsToDelete.forEach(groupId -> groupService.delete(groupId, userId));

		refreshTokenRepository.deleteByUserId(userId);
		socialAccountRepository.deleteByUserId(userId);
		emailVerificationRepository.deleteByEmail(email);
		fileService.deleteProfileImage(userId);
		// 아래 세 갱신은 영속성 컨텍스트를 비우므로(clearAutomatically) 사용자 삭제보다 먼저 끝낸다.
		fileService.clearUploader(userId);
		groupSpaceRepository.clearCreatedBy(userId);
		groupInvitationRepository.clearCreatedBy(userId);
		userRepository.deleteById(userId);
	}

	private boolean hasOtherMembership(Long groupId, Long userId) {
		return groupMembershipRepository.findByGroupId(groupId).stream()
				.anyMatch(membership -> !membership.getUserId().equals(userId));
	}

	/**
	 * 권한 이전 요청을 검증해 {@code groupId -> newOwnerUserId} 로 정리한다.
	 * 넘겨야 할 모임이 하나라도 빠지면 탈퇴 자체를 막는다 — 총무 없는 모임이 남으면 복구 경로가 없다.
	 */
	private Map<Long, Long> validateTransfers(List<OwnershipTransfer> requested, List<Long> needsTransfer,
			Long userId) {
		Map<Long, Long> transfers = new HashMap<>();
		for (OwnershipTransfer transfer : requested) {
			if (!needsTransfer.contains(transfer.groupId())) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST,
						"권한을 넘길 필요가 없는 모임입니다. groupId=" + transfer.groupId());
			}
			if (transfer.newOwnerUserId().equals(userId)) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST, "본인에게는 권한을 넘길 수 없습니다.");
			}
			transfers.put(transfer.groupId(), transfer.newOwnerUserId());
		}
		if (!transfers.keySet().containsAll(needsTransfer)) {
			throw new BusinessException(ErrorCode.OWNER_TRANSFER_REQUIRED);
		}
		return transfers;
	}

	private void promoteToOwner(Long groupId, Long newOwnerUserId) {
		GroupMembership target = groupMembershipRepository.findByGroupIdAndUserId(groupId, newOwnerUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBERSHIP_NOT_FOUND));
		target.changeRole(GroupRole.OWNER);
	}

	/** 사유는 통계용이라 계정과 잇지 않고 따로 남긴다. */
	private void saveReasons(WithdrawRequest request) {
		boolean etc = request.reasons().contains(WithdrawalReasonType.ETC);
		String detail = request.reasonDetail() == null ? null : request.reasonDetail().trim();
		if (etc && (detail == null || detail.isEmpty())) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "기타 사유를 입력해 주세요.");
		}
		withdrawalReasonRepository.saveAll(request.reasons().stream()
				.map(reason -> WithdrawalReason.of(reason,
						reason == WithdrawalReasonType.ETC ? detail : null))
				.toList());
	}

	/** 화면이 소셜 아이콘을 그리고 「비밀번호 변경」 노출을 판단하는 값. */
	private String loginProviderOf(User user) {
		if (user.hasPassword()) {
			return EMAIL_PROVIDER;
		}
		return socialAccountRepository.findByUserId(user.getId()).stream()
				.findFirst()
				.map(SocialAccount::getProvider)
				.map(Enum::name)
				.orElse(EMAIL_PROVIDER);
	}

	private User requireUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}
}
