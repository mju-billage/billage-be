package com.billage.member;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.group.GroupSpace;
import com.billage.member.dto.MemberCreateRequest;
import com.billage.member.dto.MemberResponse;
import com.billage.membership.GroupAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * 납부 관리용 모임원 명단. 가입 사용자·관리자 관계와 자동 연결하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public List<MemberResponse> getMembers(Long groupId, Long userId, String keyword) {
		guard.requireMembership(groupId, userId);
		String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

		return memberRepository.search(groupId, normalizedKeyword).stream()
				.map(MemberResponse::from)
				.toList();
	}

	@Transactional
	public MemberResponse addMember(Long groupId, Long userId, MemberCreateRequest request) {
		GroupSpace group = guard.requireOwner(groupId, userId).getGroup();

		return MemberResponse.from(memberRepository.save(Member.create(group, request.name().trim())));
	}

	/**
	 * 모임원 삭제. 회비 참여 데이터까지 완전 삭제하며, 관리자 권한(GroupMembership)에는 영향을 주지 않는다.
	 * 회비 도메인 구현 시 이 메서드에 삭제 대상을 추가해야 한다.
	 */
	@Transactional
	public void removeMember(Long groupId, Long userId, Long memberId) {
		guard.requireOwner(groupId, userId);

		Member member = memberRepository.findByIdAndGroupId(memberId, groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		memberRepository.delete(member);
	}
}
