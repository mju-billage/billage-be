package com.billage.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dues.DuesService;
import com.billage.dues.dto.DuesCreateRequest;
import com.billage.dues.dto.PaymentStatusUpdateRequest;
import com.billage.folder.FolderService;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.member.dto.MemberCreateRequest;
import com.billage.member.dto.MemberPaymentListResponse;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 「모임원 상세 > 납부 내역」의 검색·정렬·페이지. 페이지 없이 부르던 기존 호출이
 * 그대로 전부를 받는지도 함께 확인한다.
 */
class MemberPaymentListTest extends IntegrationTest {

	@Autowired
	MemberService memberService;
	@Autowired
	DuesService duesService;
	@Autowired
	GroupService groupService;
	@Autowired
	FolderService folderService;
	@Autowired
	LedgerService ledgerService;
	@Autowired
	UserRepository userRepository;

	private Long ownerId;
	private Long outsiderId;
	private Long groupId;
	private Long folderId;
	private Long memberId;

	@BeforeEach
	void setUp() {
		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		outsiderId = userRepository.save(User.create("out@example.com", "encoded", "남의모임")).getId();
		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
		folderId = folderService.create(groupId, ownerId, new FolderCreateRequest("2026", null)).folderId();
		memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, null, null)).memberId();

		Long operations = ledger("운영 장부");
		Long festival = ledger("축제 장부");
		// 납부 확인 순서가 곧 납부 시각 순서다: 1학기 → 2학기 → 축제
		pay("1학기 회비", 30_000L, operations);
		pay("2학기 회비", 30_000L, operations);
		pay("참가비", 10_000L, festival);
	}

	@Test
	void 페이지_없이_부르면_전부를_최신_납부순으로_내려준다() {
		MemberPaymentListResponse response = list(null, null, null, null);

		assertThat(response.payments()).extracting(MemberPaymentListResponse.Payment::duesTitle)
				.containsExactly("참가비", "2학기 회비", "1학기 회비");
		assertThat(response.totalPaidAmount()).isEqualTo(70_000L);
		assertThat(response.pageInfo().totalElements()).isEqualTo(3);
		assertThat(response.pageInfo().last()).isTrue();
	}

	@Test
	void 페이지를_주면_그만큼만_내려주고_전체_개수를_알려준다() {
		MemberPaymentListResponse first = list(null, null, 0, 2);
		MemberPaymentListResponse second = list(null, null, 1, 2);

		assertThat(first.payments()).extracting(MemberPaymentListResponse.Payment::duesTitle)
				.containsExactly("참가비", "2학기 회비");
		assertThat(first.pageInfo().totalElements()).isEqualTo(3);
		assertThat(first.pageInfo().totalPages()).isEqualTo(2);
		assertThat(first.pageInfo().first()).isTrue();
		assertThat(first.pageInfo().last()).isFalse();
		assertThat(second.payments()).extracting(MemberPaymentListResponse.Payment::duesTitle)
				.containsExactly("1학기 회비");
		assertThat(second.pageInfo().last()).isTrue();
		// 총 납부 금액은 페이지와 무관하다.
		assertThat(second.totalPaidAmount()).isEqualTo(70_000L);
	}

	@Test
	void 오래된_순으로_정렬할_수_있다() {
		MemberPaymentListResponse response = list(null, "paidAt,asc", null, null);

		assertThat(response.payments()).extracting(MemberPaymentListResponse.Payment::duesTitle)
				.containsExactly("1학기 회비", "2학기 회비", "참가비");
	}

	@Test
	void 회비명으로_검색한다() {
		MemberPaymentListResponse response = list("학기", null, null, null);

		assertThat(response.payments()).extracting(MemberPaymentListResponse.Payment::duesTitle)
				.containsExactly("2학기 회비", "1학기 회비");
		assertThat(response.pageInfo().totalElements()).isEqualTo(2);
		// 총 납부 금액은 검색어와 무관하다.
		assertThat(response.totalPaidAmount()).isEqualTo(70_000L);
	}

	@Test
	void 장부명으로도_검색한다() {
		MemberPaymentListResponse response = list("축제", null, null, null);

		assertThat(response.payments()).extracting(MemberPaymentListResponse.Payment::duesTitle)
				.containsExactly("참가비");
	}

	@Test
	void 검색어의_와일드카드_문자는_글자_그대로_찾는다() {
		assertThat(list("%", null, null, null).payments()).isEmpty();
		assertThat(list("_", null, null, null).payments()).isEmpty();
		assertThat(list("%", null, null, null).pageInfo().totalElements()).isZero();
	}

	@Test
	void 허용하지_않는_정렬_기준은_거부한다() {
		assertThatThrownBy(() -> MemberPaymentPaging.of("amount,desc", null, null))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	@Test
	void 페이지_크기_상한을_넘기면_거부한다() {
		assertThatThrownBy(() -> MemberPaymentPaging.of(null, 0, MemberPaymentPaging.MAX_SIZE + 1))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	@Test
	void 다른_모임_사람은_납부_내역을_볼_수_없다() {
		assertThatThrownBy(() -> memberService.getPayments(groupId, outsiderId, memberId, null, null, null,
				MemberPaymentPaging.of(null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	private MemberPaymentListResponse list(String keyword, String sort, Integer page, Integer size) {
		return memberService.getPayments(groupId, ownerId, memberId, null, null, keyword,
				MemberPaymentPaging.of(sort, page, size));
	}

	private Long ledger(String name) {
		return ledgerService.create(folderId, ownerId, new LedgerCreateRequest(name, null)).ledgerId();
	}

	private void pay(String title, long amount, Long ledgerId) {
		Long duesId = duesService.create(groupId, ownerId, new DuesCreateRequest(title, amount,
				LocalDate.now(), LocalDate.now().plusDays(30), List.of(memberId), ledgerId)).duesId();
		duesService.changePaymentStatus(duesId, memberId, ownerId, new PaymentStatusUpdateRequest("PAID"));
	}
}
