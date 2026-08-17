package com.billage.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.billage.auth.social.SocialAccountRepository;
import com.billage.auth.token.RefreshTokenRepository;
import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.group.GroupService;
import com.billage.group.GroupSpace;
import com.billage.group.GroupSpaceRepository;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.group.dto.GroupUpdateRequest;
import com.billage.member.MemberRepository;
import com.billage.member.MemberService;
import com.billage.member.dto.MemberCreateRequest;
import com.billage.membership.dto.RoleUpdateRequest;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 관리자 관계(GroupMembership)와 납부 명단(Member)의 분리 규칙 검증.
 * 두 개념이 섞이면 회비 대상과 로그인 권한이 뒤엉키므로 핵심 규칙으로 다룬다.
 */
class GroupMembershipServiceTest extends IntegrationTest {

	@Autowired
	GroupService groupService;
	@Autowired
	GroupMembershipService groupMembershipService;
	@Autowired
	MemberService memberService;
	@Autowired
	GroupMembershipRepository groupMembershipRepository;
	@Autowired
	GroupInvitationRepository groupInvitationRepository;
	@Autowired
	MemberRepository memberRepository;
	@Autowired
	GroupSpaceRepository groupSpaceRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	RefreshTokenRepository refreshTokenRepository;
	@Autowired
	SocialAccountRepository socialAccountRepository;

	private Long ownerId;
	private Long adminId;
	private Long groupId;

	@BeforeEach
	void setUp() {
		groupInvitationRepository.deleteAll();
		memberRepository.deleteAll();
		groupMembershipRepository.deleteAll();
		groupSpaceRepository.deleteAll();
		// 다른 테스트 클래스가 남긴 사용자 종속 데이터부터 정리해야 users 삭제가 FK에 걸리지 않는다.
		socialAccountRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();

		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "encoded", "일반관리자")).getId();
		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null)).groupId();
	}

	// --- 관리자 ↔ 모임원 명단 분리 ---

	@Test
	void 모임을_만들어도_납부_명단은_생기지_않는다() {
		assertThat(memberRepository.countByGroupId(groupId)).isZero();
		assertThat(groupMembershipRepository.countByGroupIdAndRole(groupId, GroupRole.OWNER)).isEqualTo(1);
	}

	@Test
	void 초대_코드로_참여해도_납부_명단에는_추가되지_않는다() {
		joinWithInvitation(adminId);

		assertThat(groupMembershipRepository.existsByGroupIdAndUserId(groupId, adminId)).isTrue();
		assertThat(memberRepository.countByGroupId(groupId)).isZero();
	}

	@Test
	void 모임원_등록은_관리자_권한을_만들지_않는다() {
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("김모임원"));

		assertThat(memberRepository.countByGroupId(groupId)).isEqualTo(1);
		assertThat(groupMembershipRepository.findByGroupId(groupId)).hasSize(1);
	}

	@Test
	void 탈퇴해도_납부_명단은_남는다() {
		joinWithInvitation(adminId);
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("김모임원"));

		groupMembershipService.leave(groupId, adminId);

		assertThat(groupMembershipRepository.existsByGroupIdAndUserId(groupId, adminId)).isFalse();
		assertThat(memberRepository.countByGroupId(groupId)).isEqualTo(1);
	}

	// --- 총무 최소 1명 ---

	@Test
	void 마지막_총무는_권한을_해제할_수_없다() {
		Long ownerMembershipId = membershipIdOf(ownerId);

		assertThatThrownBy(() -> groupMembershipService.changeRole(groupId, ownerId, ownerMembershipId,
				new RoleUpdateRequest("MEMBER")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.LAST_OWNER_REQUIRED);
	}

	@Test
	void 마지막_총무는_탈퇴할_수_없다() {
		assertThatThrownBy(() -> groupMembershipService.leave(groupId, ownerId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.LAST_OWNER_REQUIRED);
	}

	@Test
	void 공동_총무가_있으면_권한을_넘기고_탈퇴할_수_있다() {
		joinWithInvitation(adminId);
		groupMembershipService.changeRole(groupId, ownerId, membershipIdOf(adminId), new RoleUpdateRequest("OWNER"));

		groupMembershipService.leave(groupId, ownerId);

		assertThat(groupMembershipRepository.countByGroupIdAndRole(groupId, GroupRole.OWNER)).isEqualTo(1);
		assertThat(groupMembershipRepository.existsByGroupIdAndUserId(groupId, ownerId)).isFalse();
	}

	// --- 초대 코드 ---

	@Test
	void 이미_참여한_모임에는_다시_참여할_수_없다() {
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(adminId, code);

		assertThatThrownBy(() -> groupMembershipService.join(adminId, code))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ALREADY_GROUP_MEMBER);
	}

	@Test
	void 만료된_초대_코드로는_참여할_수_없다() {
		GroupSpace group = groupSpaceRepository.findById(groupId).orElseThrow();
		groupInvitationRepository.save(GroupInvitation.issue(group, "EXPIREDXXX", ownerId,
				LocalDateTime.now().minusMinutes(1)));

		assertThatThrownBy(() -> groupMembershipService.join(adminId, "EXPIREDXXX"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVITATION_EXPIRED);
	}

	@Test
	void 존재하지_않는_초대_코드는_거부된다() {
		assertThatThrownBy(() -> groupMembershipService.join(adminId, "NOSUCHCODE"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INVITATION_CODE);
	}

	// --- 내 모임 목록(JPQL 생성자 프로젝션) ---

	@Test
	void 내_모임_목록은_내_역할과_명단_인원수를_함께_준다() {
		joinWithInvitation(adminId);
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("김모임원"));
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("이모임원"));

		var mine = groupService.getMyGroups(ownerId);
		var admins = groupService.getMyGroups(adminId);

		// memberCount 는 관리자 수가 아니라 납부 명단 수다.
		assertThat(mine).singleElement().satisfies(group -> {
			assertThat(group.groupId()).isEqualTo(groupId);
			assertThat(group.myRole()).isEqualTo(GroupRole.OWNER);
			assertThat(group.memberCount()).isEqualTo(2);
		});
		assertThat(admins).singleElement()
				.satisfies(group -> assertThat(group.myRole()).isEqualTo(GroupRole.MEMBER));
	}

	@Test
	void 모임_이름을_공백만으로_수정할_수_없다() {
		assertThatThrownBy(() -> groupService.update(groupId, ownerId, new GroupUpdateRequest("   ", null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);

		// 이름을 생략한 부분 수정은 그대로 동작해야 한다.
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, "설명만 변경"));
		assertThat(groupSpaceRepository.findById(groupId).orElseThrow().getName()).isEqualTo("주리랑");
	}

	// --- 모임 삭제 ---

	@Test
	void 모임_삭제_시_관리자_명단_초대코드가_함께_삭제된다() {
		joinWithInvitation(adminId);
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("김모임원"));

		groupService.delete(groupId, ownerId);

		assertThat(groupSpaceRepository.findById(groupId)).isEmpty();
		assertThat(groupMembershipRepository.findByGroupId(groupId)).isEmpty();
		assertThat(memberRepository.countByGroupId(groupId)).isZero();
		assertThat(groupInvitationRepository.count()).isZero();
	}

	private void joinWithInvitation(Long userId) {
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(userId, code);
	}

	private Long membershipIdOf(Long userId) {
		return groupMembershipRepository.findByGroupIdAndUserId(groupId, userId).orElseThrow().getId();
	}
}
