package com.billage.group;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 모임 리소스 접근 권한 공통 검증. 컨트롤러가 아니라 각 도메인 Service에서 호출한다.
 * <p>
 * 권한 주체는 {@link GroupManager}(OWNER/GENERAL)이며, 모임원 명단(GroupMember)은 권한과 무관하다.
 * 리소스 ID 조회만으로 반환하지 말고, 요청자가 해당 모임의 관리자인지 먼저 확인해야 한다.
 */
@Component
@RequiredArgsConstructor
public class GroupAccessGuard {

	private final GroupManagerRepository groupManagerRepository;

	/** 요청자가 모임 관리자인지 확인하고 GroupManager를 반환한다. 아니면 {@code NOT_GROUP_MANAGER}. */
	@Transactional(readOnly = true)
	public GroupManager requireManager(Long userId, Long groupId) {
		return groupManagerRepository.findByGroupIdAndUserId(groupId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MANAGER));
	}

	/** 요청자가 총무(OWNER)인지 확인한다. 관리자가 아니면 {@code NOT_GROUP_MANAGER}, GENERAL이면 {@code NOT_GROUP_OWNER}. */
	@Transactional(readOnly = true)
	public GroupManager requireOwner(Long userId, Long groupId) {
		GroupManager manager = requireManager(userId, groupId);
		if (!manager.isOwner()) {
			throw new BusinessException(ErrorCode.NOT_GROUP_OWNER);
		}
		return manager;
	}
}
