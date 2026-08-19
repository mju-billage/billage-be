package com.billage.member;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dues.DuesService;
import com.billage.group.GroupSpace;
import com.billage.member.dto.MemberCreateRequest;
import com.billage.member.dto.MemberResponse;
import com.billage.member.dto.MemberUpdateRequest;
import com.billage.membership.GroupAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * 납부 관리용 모임원 명단. 가입 사용자·관리자 관계와 자동 연결하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;
	private final DuesService duesService;
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
	 * 모임원 이름 수정(총무 전용). 오타 정정 용도이며 회비 참여 데이터는 그대로 유지된다.
	 */
	@Transactional
	public MemberResponse updateMember(Long groupId, Long userId, Long memberId, MemberUpdateRequest request) {
		guard.requireOwner(groupId, userId);

		Member member = memberRepository.findByIdAndGroupId(memberId, groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		String name = request.name().trim();
		if (name.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "모임원 이름은 공백일 수 없습니다.");
		}
		member.rename(name);

		return MemberResponse.from(member);
	}

	/**
	 * 모임원 삭제. 회비 참여 데이터까지 완전 삭제하며, 관리자 권한(GroupMembership)에는 영향을 주지 않는다.
	 */
	@Transactional
	public void removeMember(Long groupId, Long userId, Long memberId) {
		guard.requireOwner(groupId, userId);

		Member member = memberRepository.findByIdAndGroupId(memberId, groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		// 명단에서 지우면 그 사람의 회비 참여 데이터도 완전 삭제한다(기획 확정).
		duesService.deleteByMember(memberId);
		memberRepository.delete(member);
	}
}
