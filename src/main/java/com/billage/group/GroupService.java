package com.billage.group;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.group.dto.GroupResponse;
import com.billage.group.dto.GroupSummaryResponse;

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
