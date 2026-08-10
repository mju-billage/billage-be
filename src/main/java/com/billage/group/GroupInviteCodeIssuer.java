package com.billage.group;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 초대 코드 발급을 <b>독립 트랜잭션</b>으로 수행한다.
 * <p>
 * 코드 중복은 {@code uk_groups_invite_code} 제약으로만 확실히 걸러진다. 제약 위반이 나면 그 트랜잭션은
 * 롤백 표시가 되어 같은 트랜잭션 안에서는 재시도할 수 없으므로, 호출 측({@link GroupService})이
 * 새 트랜잭션으로 다시 시도할 수 있도록 {@code REQUIRES_NEW}로 분리했다.
 * <p>
 * 각 메서드는 {@code saveAndFlush}로 제약 위반을 커밋 전에 드러내
 * {@code DataIntegrityViolationException}이 호출 측 재시도 루프로 전달되게 한다.
 */
@Component
@RequiredArgsConstructor
public class GroupInviteCodeIssuer {

	private final GroupRepository groupRepository;
	private final GroupManagerRepository groupManagerRepository;
	private final InviteCodeGenerator inviteCodeGenerator;

	/** 모임을 만들고 생성자를 OWNER 관리자로 등록한다. 초대 코드가 중복이면 예외가 호출 측으로 전달된다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Group createGroupWithOwner(String name, Long userId) {
		Group group = groupRepository.saveAndFlush(Group.create(name, inviteCodeGenerator.generate()));
		groupManagerRepository.save(GroupManager.owner(group, userId));
		return group;
	}

	/** 초대 코드를 새로 발급한다. 권한 검증은 호출 측에서 이미 끝난 상태여야 한다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Group regenerateInviteCode(Long groupId) {
		Group group = groupRepository.findById(groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
		group.changeInviteCode(inviteCodeGenerator.generate());
		return groupRepository.saveAndFlush(group);
	}
}
