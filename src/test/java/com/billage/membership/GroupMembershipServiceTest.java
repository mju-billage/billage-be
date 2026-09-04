package com.billage.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.entry.EntryRepository;
import com.billage.entry.EntryService;
import com.billage.entry.EntryType;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.folder.FolderService;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.group.GroupService;
import com.billage.group.GroupSpace;
import com.billage.group.GroupSpaceRepository;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.group.dto.GroupUpdateRequest;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.member.MemberRepository;
import com.billage.member.MemberService;
import com.billage.member.dto.MemberCreateRequest;
import com.billage.member.dto.MemberUpdateRequest;
import com.billage.membership.dto.MembershipResponse;
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
	FolderService folderService;
	@Autowired
	LedgerService ledgerService;
	@Autowired
	EntryService entryService;
	@Autowired
	EntryRepository entryRepository;
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

	private Long ownerId;
	private Long adminId;
	private Long groupId;

	@BeforeEach
	void setUp() {

		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "encoded", "일반관리자")).getId();
		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
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
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("김모임원", null, null, null));

		assertThat(memberRepository.countByGroupId(groupId)).isEqualTo(1);
		assertThat(groupMembershipRepository.findByGroupId(groupId)).hasSize(1);
	}

	@Test
	void 탈퇴해도_납부_명단은_남는다() {
		joinWithInvitation(adminId);
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("김모임원", null, null, null));

		groupMembershipService.leave(groupId, adminId);

		assertThat(groupMembershipRepository.existsByGroupIdAndUserId(groupId, adminId)).isFalse();
		assertThat(memberRepository.countByGroupId(groupId)).isEqualTo(1);
	}

	@Test
	void 관리자_목록은_총무_먼저_그다음_이름_오름차순이다() {
		Long naId = userRepository.save(User.create("na@example.com", "encoded", "나관리")).getId();
		Long gaId = userRepository.save(User.create("ga@example.com", "encoded", "가관리")).getId();
		joinWithInvitation(naId);
		joinWithInvitation(gaId);
		// 총무(setUp 의 "총무")는 이름이 뒤여도 맨 앞이어야 한다.

		var memberships = groupMembershipService.getMemberships(groupId, ownerId);

		assertThat(memberships).extracting(MembershipResponse::name)
				.containsExactly("총무", "가관리", "나관리");
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
	void 권한_수정_응답은_목록과_같은_형태다() {
		joinWithInvitation(adminId);

		var response = groupMembershipService.changeRole(groupId, ownerId, membershipIdOf(adminId),
				new RoleUpdateRequest("OWNER"));

		// 클라이언트가 이 응답으로 목록 캐시를 갱신하므로 email·joinedAt 이 빠지면 안 된다.
		assertThat(response.role()).isEqualTo(GroupRole.OWNER);
		assertThat(response.email()).isNotNull();
		assertThat(response.joinedAt()).isNotNull();
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

	// --- 관리자 내보내기 ---

	@Test
	void 총무는_다른_관리자를_내보낼_수_있다() {
		joinWithInvitation(adminId);

		groupMembershipService.removeMembership(groupId, ownerId, membershipIdOf(adminId));

		assertThat(groupMembershipRepository.existsByGroupIdAndUserId(groupId, adminId)).isFalse();
		// 관리자 관계만 끊는다. 납부 명단은 별개다.
		assertThat(groupMembershipRepository.countByGroupIdAndRole(groupId, GroupRole.OWNER)).isEqualTo(1);
	}

	@Test
	void 일반_관리자는_다른_사람을_내보낼_수_없다() {
		joinWithInvitation(adminId);
		Long otherId = userRepository.save(User.create("other@example.com", "encoded", "다른관리자")).getId();
		joinWithInvitation(otherId);

		assertThatThrownBy(() -> groupMembershipService.removeMembership(groupId, adminId, membershipIdOf(otherId)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 본인은_내보내기로_이탈할_수_없다() {
		// 총무 인수인계 규칙이 달라 「모임 나가기」와 경로를 섞지 않는다.
		assertThatThrownBy(() -> groupMembershipService.removeMembership(groupId, ownerId, membershipIdOf(ownerId)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	@Test
	void 다른_모임의_관리자는_내보낼_수_없다() {
		Long otherGroupId = groupService.create(adminId, new GroupCreateRequest("남의모임", null, null)).groupId();
		Long otherMembershipId = groupMembershipRepository.findByGroupIdAndUserId(otherGroupId, adminId)
				.orElseThrow().getId();

		assertThatThrownBy(() -> groupMembershipService.removeMembership(groupId, ownerId, otherMembershipId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.MEMBERSHIP_NOT_FOUND);
	}

	@Test
	void 내보내도_그_사람이_남긴_내역은_지워지지_않는다() {
		joinWithInvitation(adminId);
		Long ledgerId = ledgerFixture();
		Long entryId = entryService.create(ledgerId, adminId,
				new EntryCreateRequest(EntryType.EXPENSE, "회식비", 30000L, LocalDate.now(), null, null, null)).entryId();

		groupMembershipService.removeMembership(groupId, ownerId, membershipIdOf(adminId));

		// 작성자 이름은 등록 시점 값으로 남아 있어야 한다.
		assertThat(entryRepository.findById(entryId)).isPresent()
				.get().satisfies(entry -> assertThat(entry.getCreatedByName()).isEqualTo("일반관리자"));
	}

	// --- 초대 코드 ---

	@Test
	void 유효한_코드가_있으면_재발급하지_않고_같은_코드를_준다() {
		var first = groupMembershipService.createInvitation(groupId, ownerId);
		var second = groupMembershipService.createInvitation(groupId, ownerId);

		assertThat(second.invitationCode()).isEqualTo(first.invitationCode());
		assertThat(groupInvitationRepository.count()).isEqualTo(1);
	}

	@Test
	void 조회는_발급과_같은_코드를_주고_일반_관리자도_읽을_수_있다() {
		joinWithInvitation(adminId);
		String issued = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();

		var read = groupMembershipService.currentInvitation(groupId, adminId);

		assertThat(read.invitationCode()).isEqualTo(issued);
	}

	@Test
	void 유효한_코드가_없으면_일반_관리자는_발급하지_못한다() {
		joinWithInvitation(adminId);
		groupInvitationRepository.deleteAll();

		assertThatThrownBy(() -> groupMembershipService.currentInvitation(groupId, adminId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVITATION_NOT_FOUND);
		assertThat(groupInvitationRepository.count()).isZero();
	}

	@Test
	void 기존_코드가_만료됐으면_새_코드를_발급한다() {
		GroupSpace expiredGroup = groupSpaceRepository.findById(groupId).orElseThrow();
		groupInvitationRepository.save(GroupInvitation.issue(expiredGroup, "OLDCODE123", ownerId,
				LocalDateTime.now().minusMinutes(1)));

		var issued = groupMembershipService.createInvitation(groupId, ownerId);

		assertThat(issued.invitationCode()).isNotEqualTo("OLDCODE123");
	}

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
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("김모임원", null, null, null));
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("이모임원", null, null, null));

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
		assertThatThrownBy(() -> groupService.update(groupId, ownerId, new GroupUpdateRequest("   ", null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);

		// 이름을 생략한 부분 수정은 그대로 동작해야 한다.
		groupService.update(groupId, ownerId, new GroupUpdateRequest(null, "설명만 변경", null));
		assertThat(groupSpaceRepository.findById(groupId).orElseThrow().getName()).isEqualTo("주리랑");
	}

	// --- 모임원 수정 ---

	@Test
	void 총무는_모임원_이름을_고칠_수_있고_일반_관리자는_못_고친다() {
		joinWithInvitation(adminId);
		Long memberId = memberService.addMember(groupId, ownerId, new MemberCreateRequest("김모임원", null, null, null)).memberId();

		assertThat(memberService.updateMember(groupId, ownerId, memberId,
				new MemberUpdateRequest("김모임원정정", null, null, null)).name()).isEqualTo("김모임원정정");

		assertThatThrownBy(() -> memberService.updateMember(groupId, adminId, memberId,
				new MemberUpdateRequest("몰래 변경", null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 다른_모임의_모임원은_수정할_수_없다() {
		Long otherGroupId = groupService.create(adminId, new GroupCreateRequest("남의모임", null, null)).groupId();
		Long otherMemberId = memberService.addMember(otherGroupId, adminId,
				new MemberCreateRequest("남의모임원", null, null, null)).memberId();

		assertThatThrownBy(() -> memberService.updateMember(groupId, ownerId, otherMemberId,
				new MemberUpdateRequest("탈취", null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
	}

	// --- 모임 삭제 ---

	@Test
	void 모임_삭제_시_관리자_명단_초대코드가_함께_삭제된다() {
		joinWithInvitation(adminId);
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("김모임원", null, null, null));

		groupService.delete(groupId, ownerId);

		assertThat(groupSpaceRepository.findById(groupId)).isEmpty();
		assertThat(groupMembershipRepository.findByGroupId(groupId)).isEmpty();
		assertThat(memberRepository.countByGroupId(groupId)).isZero();
		assertThat(groupInvitationRepository.count()).isZero();
	}

	private Long ledgerFixture() {
		Long folderId = folderService.create(groupId, ownerId, new FolderCreateRequest("2026년", null)).folderId();
		return ledgerService.create(folderId, ownerId, new LedgerCreateRequest("회비장부", null)).ledgerId();
	}

	private void joinWithInvitation(Long userId) {
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(userId, code);
	}

	private Long membershipIdOf(Long userId) {
		return groupMembershipRepository.findByGroupIdAndUserId(groupId, userId).orElseThrow().getId();
	}
}
