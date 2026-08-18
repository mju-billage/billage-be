package com.billage.membership;

import org.springframework.stereotype.Component;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.group.GroupSpace;
import com.billage.group.GroupSpaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 모임 리소스 접근 권한 검증. 모든 모임 하위 도메인(명단·폴더·장부·내역·회비 등)은
 * 리소스를 ID로 조회하기 전에 반드시 이 가드를 통과해야 한다.
 *
 * <p>권한은 관리자 관계(GroupMembership) 로만 판정한다 — 납부 명단(Member) 에는 권한이 없다.
 * 존재하지 않는 모임은 404, 관리자가 아닌 모임은 403으로 구분해 응답한다.
 */
@Component
@RequiredArgsConstructor
public class GroupAccessGuard {

	private final GroupSpaceRepository groupSpaceRepository;
	private final GroupMembershipRepository groupMembershipRepository;

	public GroupSpace getGroup(Long groupId) {
		return groupSpaceRepository.findById(groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
	}

	/** 모임 관리자인지 검증하고 요청자의 GroupMembership 을 반환한다. */
	public GroupMembership requireMembership(Long groupId, Long userId) {
		getGroup(groupId);
		return groupMembershipRepository.findByGroupIdAndUserId(groupId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
	}

	/** 총무(OWNER)인지 검증하고 요청자의 GroupMembership 을 반환한다. */
	public GroupMembership requireOwner(Long groupId, Long userId) {
		GroupMembership membership = requireMembership(groupId, userId);
		if (!membership.isOwner()) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}
		return membership;
	}
}
