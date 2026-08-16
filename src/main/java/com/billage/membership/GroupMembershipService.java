package com.billage.membership;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.group.GroupSpace;
import com.billage.membership.dto.InvitationResponse;
import com.billage.membership.dto.JoinGroupResponse;
import com.billage.membership.dto.MembershipResponse;
import com.billage.membership.dto.RoleUpdateRequest;
import com.billage.membership.dto.RoleUpdateResponse;
import com.billage.user.User;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 모임 관리자 관계(권한·초대·참여·탈퇴). 납부 명단(Member)은 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GroupMembershipService {

	/** 초대 코드 유효 기간. 프론트와 합의된 값. */
	private static final Duration INVITATION_VALIDITY = Duration.ofDays(7);
	/** 오독하기 쉬운 문자(0/O, 1/I)를 제외한 코드 알파벳. */
	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 10;
	private static final int CODE_RETRY = 5;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final GroupMembershipRepository groupMembershipRepository;
	private final GroupInvitationRepository groupInvitationRepository;
	private final UserRepository userRepository;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public List<MembershipResponse> getMemberships(Long groupId, Long userId) {
		guard.requireMembership(groupId, userId);

		List<GroupMembership> memberships = groupMembershipRepository.findByGroupId(groupId);
		Map<Long, String> names = userNames(memberships);

		return memberships.stream()
				.map(ms -> MembershipResponse.of(ms, names.get(ms.getUserId())))
				.toList();
	}

	@Transactional
	public InvitationResponse createInvitation(Long groupId, Long userId) {
		GroupSpace group = guard.requireOwner(groupId, userId).getGroup();
		GroupInvitation invitation = GroupInvitation.issue(group, generateUniqueCode(), userId,
				LocalDateTime.now().plus(INVITATION_VALIDITY));

		return InvitationResponse.from(groupInvitationRepository.save(invitation));
	}

	/**
	 * 초대 코드로 모임 참여. 관리자 관계만 만들고 납부 명단은 생성하지 않는다.
	 * 동시 요청으로 같은 사용자가 두 번 등록되는 것은 {@code (group_id, user_id)} 유니크 제약으로 막는다.
	 */
	@Transactional
	public JoinGroupResponse join(Long userId, String invitationCode) {
		GroupInvitation invitation = groupInvitationRepository.findByCode(invitationCode.trim().toUpperCase())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITATION_CODE));
		if (invitation.isExpired(LocalDateTime.now())) {
			throw new BusinessException(ErrorCode.INVITATION_EXPIRED);
		}

		GroupSpace group = invitation.getGroup();
		if (groupMembershipRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
			throw new BusinessException(ErrorCode.ALREADY_GROUP_MEMBER);
		}

		try {
			GroupMembership membership = groupMembershipRepository.saveAndFlush(
					GroupMembership.join(group, userId));
			return JoinGroupResponse.from(membership);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.ALREADY_GROUP_MEMBER);
		}
	}

	/**
	 * 관리자 권한 수정. 마지막 총무의 권한은 해제할 수 없다.
	 */
	@Transactional
	public RoleUpdateResponse changeRole(Long groupId, Long userId, Long membershipId, RoleUpdateRequest request) {
		guard.requireOwner(groupId, userId);
		GroupRole newRole = request.toGroupRole();

		GroupMembership target = groupMembershipRepository.findByIdAndGroupId(membershipId, groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBERSHIP_NOT_FOUND));
		if (target.isOwner() && newRole != GroupRole.OWNER) {
			requireNotLastOwner(groupId);
		}
		target.changeRole(newRole);

		return RoleUpdateResponse.of(target, userName(target.getUserId()));
	}

	/**
	 * 모임 탈퇴. 유일한 총무는 다른 관리자에게 권한을 넘긴 뒤에만 탈퇴할 수 있다.
	 * 별도로 등록된 납부 명단(Member)은 함께 삭제하지 않는다.
	 */
	@Transactional
	public void leave(Long groupId, Long userId) {
		GroupMembership me = guard.requireMembership(groupId, userId);
		if (me.isOwner()) {
			requireNotLastOwner(groupId);
		}
		groupMembershipRepository.delete(me);
	}

	private void requireNotLastOwner(Long groupId) {
		if (groupMembershipRepository.countByGroupIdAndRole(groupId, GroupRole.OWNER) <= 1) {
			throw new BusinessException(ErrorCode.LAST_OWNER_REQUIRED);
		}
	}

	private Map<Long, String> userNames(List<GroupMembership> memberships) {
		List<Long> userIds = memberships.stream().map(GroupMembership::getUserId).toList();
		return userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(User::getId, User::getName));
	}

	private String userName(Long userId) {
		return userRepository.findById(userId).map(User::getName)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	private String generateUniqueCode() {
		for (int attempt = 0; attempt < CODE_RETRY; attempt++) {
			String code = randomCode();
			if (!groupInvitationRepository.existsByCode(code)) {
				return code;
			}
		}
		throw new BusinessException(ErrorCode.INTERNAL_ERROR, "초대 코드 생성에 실패했습니다. 다시 시도해 주세요.");
	}

	private String randomCode() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
		}
		return code.toString();
	}
}
