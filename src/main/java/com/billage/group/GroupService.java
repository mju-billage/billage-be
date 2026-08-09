package com.billage.group;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.group.dto.GroupManagerResponse;
import com.billage.group.dto.GroupResponse;
import com.billage.group.dto.GroupSummaryResponse;
import com.billage.group.dto.InviteCodeResponse;
import com.billage.user.User;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupService {

	/** 초대 코드 유일성 확보를 위한 재생성 시도 횟수. */
	private static final int INVITE_CODE_MAX_ATTEMPTS = 5;

	private final GroupRepository groupRepository;
	private final GroupManagerRepository groupManagerRepository;
	private final GroupAccessGuard groupAccessGuard;
	private final InviteCodeGenerator inviteCodeGenerator;
	private final UserRepository userRepository;

	/**
	 * 모임 생성. 생성자는 OWNER 관리자로 등록된다.
	 * 모임원(GroupMember)은 자동 생성하지 않는다 — 총무가 [모임원 추가]로 별도 등록한다.
	 */
	@Transactional
	public GroupResponse createGroup(Long userId, String name) {
		Group group = groupRepository.save(Group.create(name, generateUniqueInviteCode()));
		groupManagerRepository.save(GroupManager.owner(group, userId));
		return GroupResponse.of(group, ManagerRole.OWNER);
	}

	/** "내 모임" 목록. GroupManager로 연결된 모임만 조회한다. */
	@Transactional(readOnly = true)
	public List<GroupSummaryResponse> getMyGroups(Long userId) {
		return groupManagerRepository.findWithGroupByUserId(userId).stream()
				.map(gm -> GroupSummaryResponse.of(gm.getGroup(), gm.getRole()))
				.toList();
	}

	/** 모임 상세. 요청자가 해당 모임의 관리자인지 검증한 뒤 반환한다. */
	@Transactional(readOnly = true)
	public GroupResponse getGroup(Long userId, Long groupId) {
		GroupManager manager = groupAccessGuard.requireManager(userId, groupId);
		return GroupResponse.of(manager.getGroup(), manager.getRole());
	}

	/**
	 * 초대 코드로 모임에 참여한다. 참여자는 일반 관리자(GENERAL)가 될 뿐
	 * <b>모임원(GroupMember)으로 자동 등록되지 않는다</b> — 총무가 [모임원 추가]로 별도 등록한다.
	 */
	@Transactional
	public GroupResponse joinByInviteCode(Long userId, String rawInviteCode) {
		Group group = groupRepository.findByInviteCode(normalizeInviteCode(rawInviteCode))
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_INVALID));
		if (!group.isActive()) {
			throw new BusinessException(ErrorCode.GROUP_NOT_ACTIVE);
		}
		if (groupManagerRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
			throw new BusinessException(ErrorCode.ALREADY_GROUP_MANAGER);
		}
		try {
			groupManagerRepository.saveAndFlush(GroupManager.general(group, userId));
		} catch (DataIntegrityViolationException e) {
			// 같은 사용자가 동시에 두 번 참여를 시도한 경우 — (group_id, user_id) 유일 제약이 막는다
			throw new BusinessException(ErrorCode.ALREADY_GROUP_MANAGER);
		}
		return GroupResponse.of(group, ManagerRole.GENERAL);
	}

	/** 초대 코드 조회. 관리자면 누구나 공유할 수 있다. */
	@Transactional(readOnly = true)
	public InviteCodeResponse getInviteCode(Long userId, Long groupId) {
		return InviteCodeResponse.of(groupAccessGuard.requireManager(userId, groupId).getGroup());
	}

	/** 초대 코드 재발급. 총무(OWNER)만 가능하며 이전 코드는 즉시 무효가 된다. */
	@Transactional
	public InviteCodeResponse regenerateInviteCode(Long userId, Long groupId) {
		Group group = groupAccessGuard.requireOwner(userId, groupId).getGroup();
		group.changeInviteCode(generateUniqueInviteCode());
		return InviteCodeResponse.of(group);
	}

	/** 모임 관리자 목록. 모임원 명단이 아니라 권한 보유자 목록이다. */
	@Transactional(readOnly = true)
	public List<GroupManagerResponse> getManagers(Long userId, Long groupId) {
		groupAccessGuard.requireManager(userId, groupId);
		List<GroupManager> managers = groupManagerRepository.findByGroupIdOrderByCreatedAtAsc(groupId);
		Map<Long, User> users = userRepository.findAllById(
						managers.stream().map(GroupManager::getUserId).toList()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		return managers.stream()
				.map(manager -> GroupManagerResponse.of(manager, users.get(manager.getUserId())))
				.toList();
	}

	/** 사용자가 소문자·공백을 섞어 입력해도 받아들인다. 생성되는 코드는 항상 대문자·숫자다. */
	private String normalizeInviteCode(String inviteCode) {
		return inviteCode.strip().toUpperCase(Locale.ROOT);
	}

	private String generateUniqueInviteCode() {
		for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
			String code = inviteCodeGenerator.generate();
			if (!groupRepository.existsByInviteCode(code)) {
				return code;
			}
		}
		throw new BusinessException(ErrorCode.INTERNAL_ERROR, "초대 코드 생성에 실패했습니다. 다시 시도해 주세요.");
	}
}
