package com.billage.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.member.dto.MemberBulkCreateRequest;
import com.billage.member.dto.MemberBulkDeleteRequest;
import com.billage.member.dto.MemberCreateRequest;
import com.billage.member.dto.MemberResponse;
import com.billage.member.dto.MemberUpdateRequest;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 모임원 개별 추가·일괄 추가·상세 수정(이름/전화번호/태그/메모) 검증.
 * 이름만 필수라는 규칙과, 수정이 부분 반영이 아니라 통째 교체라는 규칙이 핵심이다.
 */
class MemberProfileServiceTest extends IntegrationTest {

	@Autowired
	GroupService groupService;
	@Autowired
	MemberService memberService;
	@Autowired
	UserRepository userRepository;

	private Long ownerId;
	private Long groupId;

	@BeforeEach
	void setUp() {
		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
	}

	// --- 개별 추가 ---

	@Test
	void 이름만_있어도_등록되고_선택값은_비어_있다() {
		MemberResponse created = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, null, null));

		assertThat(created.name()).isEqualTo("김모임원");
		assertThat(created.phoneNumber()).isNull();
		assertThat(created.memo()).isNull();
		assertThat(created.tags()).isEmpty();
	}

	@Test
	void 전화번호는_하이픈을_빼고_숫자만_저장한다() {
		MemberResponse created = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", "010-1234-5678", null, null));

		assertThat(created.phoneNumber()).isEqualTo("01012345678");
	}

	@Test
	void 전화번호_자릿수가_맞지_않으면_거부된다() {
		assertThatThrownBy(() -> memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", "010-12", null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	@Test
	void 태그는_여러_개_붙고_중복과_공백은_걸러진다() {
		MemberResponse created = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, List.of("신입", " 신입 ", "", "임원"), "회비 대납"));

		assertThat(created.tags()).containsExactly("신입", "임원");
		assertThat(created.memo()).isEqualTo("회비 대납");
	}

	@Test
	void 대소문자만_다른_태그도_각각_저장된다() {
		// member_tag 는 기본 collation(ai_ci) 이면 두 값을 같은 키로 봐서 duplicate key 500 이 난다.
		MemberResponse created = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, List.of("VIP", "vip"), null));

		assertThat(created.tags()).containsExactly("VIP", "vip");
	}

	@Test
	void 숫자와_하이픈_외의_문자가_섞인_전화번호는_거부된다() {
		// 문자를 조용히 걷어내면 오입력이 멀쩡한 번호로 저장된다.
		assertThatThrownBy(() -> memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", "010a1234b5678", null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	@Test
	void 태그는_세_개까지_붙는다() {
		// 화면명세: "# 태그를 입력해 주세요 (최대 3개)"
		assertThat(memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, List.of("신입", "임원", "졸업"), null)).tags()).hasSize(3);
	}

	@Test
	void 태그가_네_개면_거부된다() {
		List<String> tooMany = List.of("신입", "임원", "졸업", "휴학");

		assertThatThrownBy(() -> memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, tooMany, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	// --- 일괄 추가 ---

	@Test
	void 일괄_추가는_쉼표_공백_줄바꿈으로_이름을_나눈다() {
		List<MemberResponse> created = memberService.addMembers(groupId, ownerId,
				new MemberBulkCreateRequest("김철수, 이영희\n박민수 최지훈\n\n"));

		assertThat(created).extracting(MemberResponse::name)
				.containsExactly("김철수", "이영희", "박민수", "최지훈");
		assertThat(created).allSatisfy(member -> {
			assertThat(member.phoneNumber()).isNull();
			assertThat(member.tags()).isEmpty();
		});
	}

	@Test
	void 일괄_추가에서_동명이인은_그대로_등록된다() {
		List<MemberResponse> created = memberService.addMembers(groupId, ownerId,
				new MemberBulkCreateRequest("김철수 김철수"));

		assertThat(created).hasSize(2);
		assertThat(created.get(0).memberId()).isNotEqualTo(created.get(1).memberId());
	}

	@Test
	void 일괄_추가에_10자를_넘는_이름이_섞이면_전부_취소된다() {
		assertThatThrownBy(() -> memberService.addMembers(groupId, ownerId,
				new MemberBulkCreateRequest("김철수, 열한글자가넘어가는긴이름")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);

		assertThat(memberService.getMembers(groupId, ownerId, null)).isEmpty();
	}

	@Test
	void 구분자만_들어오면_거부된다() {
		assertThatThrownBy(() -> memberService.addMembers(groupId, ownerId, new MemberBulkCreateRequest(" , , ")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	// --- 상세 수정 ---

	@Test
	void 수정은_보내지_않은_선택값을_비운다() {
		Long memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", "01012345678", List.of("신입"), "메모")).memberId();

		MemberResponse updated = memberService.updateMember(groupId, ownerId, memberId,
				new MemberUpdateRequest("김모임원", null, null, null));

		assertThat(updated.name()).isEqualTo("김모임원");
		assertThat(updated.phoneNumber()).isNull();
		assertThat(updated.memo()).isNull();
		assertThat(updated.tags()).isEmpty();
	}

	@Test
	void 태그를_교체하면_이전_태그는_남지_않는다() {
		Long memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, List.of("신입", "임원"), null)).memberId();

		memberService.updateMember(groupId, ownerId, memberId,
				new MemberUpdateRequest("김모임원", null, List.of("졸업"), null));

		assertThat(memberService.getMembers(groupId, ownerId, null))
				.singleElement()
				.satisfies(member -> assertThat(member.tags()).containsExactly("졸업"));
	}

	@Test
	void 목록은_태그까지_함께_준다() {
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("김모임원", "01011112222",
				List.of("임원", "신입"), "메모"));

		assertThat(memberService.getMembers(groupId, ownerId, "김"))
				.singleElement()
				.satisfies(member -> {
					assertThat(member.phoneNumber()).isEqualTo("01011112222");
					// 노출 순서는 서버가 고정한다.
					assertThat(member.tags()).containsExactly("신입", "임원");
					assertThat(member.memo()).isEqualTo("메모");
				});
	}

	@Test
	void 태그가_붙은_모임원도_삭제된다() {
		Long memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, List.of("신입"), null)).memberId();

		memberService.removeMember(groupId, ownerId, memberId);

		assertThat(memberService.getMembers(groupId, ownerId, null)).isEmpty();
	}

	// --- 상세 · 납부 내역 · 일괄 삭제 ---

	@Test
	void 상세는_총_납부_금액을_함께_준다() {
		Long memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, null, null)).memberId();

		// 아직 낸 회비가 없으면 0 이다.
		assertThat(memberService.getMember(groupId, ownerId, memberId).totalPaidAmount()).isZero();
		assertThat(memberService.getMember(groupId, ownerId, memberId).name()).isEqualTo("김모임원");
	}

	@Test
	void 다른_모임의_모임원은_상세를_볼_수_없다() {
		Long otherOwnerId = userRepository.save(User.create("other@example.com", "encoded", "남")).getId();
		Long otherGroupId = groupService.create(otherOwnerId, new GroupCreateRequest("남의모임", null, null)).groupId();
		Long otherMemberId = memberService.addMember(otherGroupId, otherOwnerId,
				new MemberCreateRequest("남의모임원", null, null, null)).memberId();

		assertThatThrownBy(() -> memberService.getMember(groupId, ownerId, otherMemberId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
	}

	@Test
	void 낸_회비가_없으면_납부_내역은_비어_있다() {
		Long memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, null, null)).memberId();

		var payments = memberService.getPayments(groupId, ownerId, memberId, null, null);

		assertThat(payments.totalPaidAmount()).isZero();
		assertThat(payments.payments()).isEmpty();
	}

	@Test
	void 여러_명을_한_번에_삭제한다() {
		Long first = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, null, null)).memberId();
		Long second = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("이모임원", null, null, null)).memberId();
		memberService.addMember(groupId, ownerId, new MemberCreateRequest("남는사람", null, null, null));

		int removed = memberService.removeMembers(groupId, ownerId, new MemberBulkDeleteRequest(
				List.of(first, second)));

		assertThat(removed).isEqualTo(2);
		assertThat(memberService.getMembers(groupId, ownerId, null))
				.singleElement()
				.satisfies(member -> assertThat(member.name()).isEqualTo("남는사람"));
	}

	@Test
	void 일괄_삭제에_다른_모임의_모임원이_섞이면_전부_취소된다() {
		Long memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, null, null)).memberId();
		Long otherOwnerId = userRepository.save(User.create("other@example.com", "encoded", "남")).getId();
		Long otherGroupId = groupService.create(otherOwnerId, new GroupCreateRequest("남의모임", null, null)).groupId();
		Long otherMemberId = memberService.addMember(otherGroupId, otherOwnerId,
				new MemberCreateRequest("남의모임원", null, null, null)).memberId();

		assertThatThrownBy(() -> memberService.removeMembers(groupId, ownerId,
				new MemberBulkDeleteRequest(List.of(memberId, otherMemberId))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

		assertThat(memberService.getMembers(groupId, ownerId, null)).hasSize(1);
	}
}
