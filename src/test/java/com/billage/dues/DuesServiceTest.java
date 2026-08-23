package com.billage.dues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dues.dto.DuesCloseResponse;
import com.billage.dues.dto.DuesCreateRequest;
import com.billage.dues.dto.DuesUpdateRequest;
import com.billage.dues.dto.PaymentStatusUpdateRequest;
import com.billage.entry.ApprovalStatus;
import com.billage.entry.EntryRepository;
import com.billage.entry.EntryType;
import com.billage.folder.FolderService;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.member.MemberService;
import com.billage.member.dto.MemberCreateRequest;
import com.billage.member.dto.MemberUpdateRequest;
import com.billage.membership.GroupMembershipService;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 회비 핵심 규칙 검증 — 총무 전용 권한, 전원 납부 시에만 마감, 마감 시 수입 내역 1건 생성,
 * 마감 후 회비와 내역의 분리, 대상자 제거 시 총액 변동.
 */
class DuesServiceTest extends IntegrationTest {

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
	DuesService duesService;
	@Autowired
	DuesRepository duesRepository;
	@Autowired
	EntryRepository entryRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	com.billage.dashboard.DashboardService dashboardService;

	private Long ownerId;
	private Long adminId;
	private Long outsiderId;
	private Long groupId;
	private Long ledgerId;
	private Long member1;
	private Long member2;

	@BeforeEach
	void setUp() {
		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "encoded", "일반관리자")).getId();
		outsiderId = userRepository.save(User.create("out@example.com", "encoded", "남의모임")).getId();

		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(adminId, code);

		Long folderId = folderService.create(groupId, ownerId,
				new FolderCreateRequest("2026년 2학기", null)).folderId();
		ledgerId = ledgerService.create(folderId, ownerId,
				new LedgerCreateRequest("운영 장부", null)).ledgerId();

		member1 = memberService.addMember(groupId, ownerId, new MemberCreateRequest("김모임원", null, null, null)).memberId();
		member2 = memberService.addMember(groupId, ownerId, new MemberCreateRequest("이모임원", null, null, null)).memberId();
	}

	// --- 권한 (전부 총무 전용) ---

	@Test
	void 일반_관리자는_회비를_만들거나_마감하거나_납부상태를_바꿀_수_없다() {
		Long duesId = createDues(List.of(member1));

		assertThatThrownBy(() -> duesService.create(groupId, adminId, createRequest(List.of(member1))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);

		assertThatThrownBy(() -> duesService.changePaymentStatus(duesId, member1, adminId,
				new PaymentStatusUpdateRequest("PAID")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);

		assertThatThrownBy(() -> duesService.close(duesId, adminId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);

		// 조회는 가능해야 한다.
		assertThat(duesService.getDetail(duesId, adminId).duesId()).isEqualTo(duesId);
	}

	@Test
	void 다른_모임_사람은_회비를_조회할_수_없다() {
		Long duesId = createDues(List.of(member1));

		assertThatThrownBy(() -> duesService.getDetail(duesId, outsiderId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	// --- 마감 ---

	@Test
	void 미납자가_있으면_마감할_수_없다() {
		Long duesId = createDues(List.of(member1, member2));
		pay(duesId, member1);

		assertThatThrownBy(() -> duesService.close(duesId, ownerId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.UNPAID_MEMBER_EXISTS);
	}

	@Test
	void 전원_납부하면_마감되고_수입_내역_1건이_즉시_승인_상태로_생긴다() {
		Long duesId = createDues(List.of(member1, member2));
		pay(duesId, member1);
		pay(duesId, member2);

		DuesCloseResponse closed = duesService.close(duesId, ownerId);

		assertThat(closed.status()).isEqualTo(DuesStatus.CLOSED);
		assertThat(closed.totalCollectedAmount()).isEqualTo(60_000L);
		assertThat(closed.generatedEntryId()).isNotNull();

		var entry = entryRepository.findById(closed.generatedEntryId()).orElseThrow();
		assertThat(entry.getType()).isEqualTo(EntryType.INCOME);
		assertThat(entry.getTitle()).isEqualTo("2학기 회비");
		assertThat(entry.getAmount()).isEqualTo(60_000L);
		// 총무의 행위이므로 승인 대기를 거치지 않는다.
		assertThat(entry.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
	}

	@Test
	void 이미_마감된_회비는_다시_마감하거나_수정할_수_없다() {
		Long duesId = closeWithAllPaid();

		assertThatThrownBy(() -> duesService.close(duesId, ownerId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.DUES_ALREADY_CLOSED);

		assertThatThrownBy(() -> duesService.update(duesId, ownerId,
				new DuesUpdateRequest("바꾼 제목", null, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.DUES_ALREADY_CLOSED);
	}

	// --- 마감 회비와 수입 내역의 분리 (기획 확정) ---

	@Test
	void 마감된_회비를_지워도_수입_내역은_장부에_남는다() {
		Long duesId = closeWithAllPaid();
		Long entryId = duesRepository.findById(duesId).orElseThrow().getGeneratedEntryId();

		duesService.delete(duesId, ownerId);

		assertThat(duesRepository.findById(duesId)).isEmpty();
		assertThat(entryRepository.findById(entryId)).isPresent();
		assertThat(ledgerService.getDetail(ledgerId, ownerId).totalIncome()).isEqualTo(60_000L);
	}

	// --- 대상자 변경 ---

	@Test
	void 납부_완료자를_대상에서_빼면_총액이_그만큼_줄어든다() {
		Long duesId = createDues(List.of(member1, member2));
		pay(duesId, member1);
		pay(duesId, member2);

		// 막지 않는다 — 총액이 달라지는 것이 정상 동작이고 보정은 총무가 수기로 한다.
		duesService.update(duesId, ownerId, new DuesUpdateRequest(null, null, List.of(member1), null, null));

		var detail = duesService.getDetail(duesId, ownerId);
		assertThat(detail.targetCount()).isEqualTo(1);
		assertThat(detail.paidCount()).isEqualTo(1);
		assertThat(duesService.close(duesId, ownerId).totalCollectedAmount()).isEqualTo(30_000L);
	}

	@Test
	void 대상자를_추가해도_기존_납부_상태는_유지된다() {
		Long duesId = createDues(List.of(member1));
		pay(duesId, member1);

		duesService.update(duesId, ownerId,
				new DuesUpdateRequest(null, null, List.of(member1, member2), null, null));

		var detail = duesService.getDetail(duesId, ownerId);
		assertThat(detail.targetCount()).isEqualTo(2);
		assertThat(detail.paidCount()).isEqualTo(1);
	}

	@Test
	void 금액은_수정할_수_없다() {
		Long duesId = createDues(List.of(member1));

		assertThatThrownBy(() -> duesService.update(duesId, ownerId,
				new DuesUpdateRequest(null, null, null, null, 50_000L)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.DUES_AMOUNT_IMMUTABLE);
	}

	@Test
	void 다른_모임의_모임원이나_장부는_지정할_수_없다() {
		Long otherGroupId = groupService.create(outsiderId, new GroupCreateRequest("남의모임", null, null)).groupId();
		Long otherMemberId = memberService.addMember(otherGroupId, outsiderId,
				new MemberCreateRequest("남의모임원", null, null, null)).memberId();

		assertThatThrownBy(() -> duesService.create(groupId, ownerId,
				new DuesCreateRequest("2학기 회비", 30_000L, LocalDate.of(2026, 9, 30),
						List.of(otherMemberId), ledgerId)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.GROUP_MISMATCH);
	}

	// --- 납부 상태 ---

	@Test
	void 납부_완료를_미납으로_되돌리면_납부_시각도_지워진다() {
		Long duesId = createDues(List.of(member1));
		pay(duesId, member1);

		duesService.changePaymentStatus(duesId, member1, ownerId, new PaymentStatusUpdateRequest("UNPAID"));

		assertThat(duesService.getTargets(duesId, ownerId, null, null)).singleElement()
				.satisfies(target -> {
					assertThat(target.status()).isEqualTo(PaymentStatus.UNPAID);
					assertThat(target.paidAt()).isNull();
				});
	}

	@Test
	void 허용되지_않은_납부_상태값은_거부된다() {
		Long duesId = createDues(List.of(member1));

		assertThatThrownBy(() -> duesService.changePaymentStatus(duesId, member1, ownerId,
				new PaymentStatusUpdateRequest("PARTIAL")))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS);
	}

	// --- 연쇄 삭제 ---

	@Test
	void 모임원을_명단에서_지우면_회비_참여_데이터도_사라진다() {
		Long duesId = createDues(List.of(member1, member2));

		memberService.removeMember(groupId, ownerId, member1);

		assertThat(duesService.getTargets(duesId, ownerId, null, null)).hasSize(1);
		assertThat(duesService.getDetail(duesId, ownerId).targetCount()).isEqualTo(1);
	}

	@Test
	void 모임원_이름을_고쳐도_회비_납부_기록은_유지된다() {
		Long duesId = createDues(List.of(member1, member2));
		pay(duesId, member1);
		OffsetDateTime paidAtBeforeRename = paidAtOf(duesId, member1);
		assertThat(paidAtBeforeRename).isNotNull();

		memberService.updateMember(groupId, ownerId, member1, new MemberUpdateRequest("김모임원정정", null, null, null));

		// 지우고 다시 만들면 납부 기록이 사라진다 — 그래서 수정 API 가 필요하다.
		assertThat(duesService.getTargets(duesId, ownerId, null, null))
				.filteredOn(target -> target.memberId().equals(member1))
				.singleElement()
				.satisfies(target -> {
					assertThat(target.name()).isEqualTo("김모임원정정");
					assertThat(target.status()).isEqualTo(PaymentStatus.PAID);
					// 이름만 바뀌어야 한다 — 납부 시각이 갱신되면 이름 수정이 재납부처럼 기록된다.
					assertThat(target.paidAt()).isEqualTo(paidAtBeforeRename);
				});
	}

	@Test
	void 모임을_삭제하면_회비도_함께_사라진다() {
		createDues(List.of(member1));

		groupService.delete(groupId, ownerId);

		assertThat(duesRepository.findAllByGroupId(groupId)).isEmpty();
	}

	@Test
	void 대시보드는_진행_중인_회비만_집계한다() {
		Long open = createDues(List.of(member1, member2));
		pay(open, member1);
		closeWithAllPaid();

		var dues = dashboardService.getDashboard(groupId, ownerId, 5).dues();

		// 마감분은 이미 장부 내역으로 반영돼 있어 회비 집계에서 빠진다.
		assertThat(dues.activeDuesCount()).isEqualTo(1);
		assertThat(dues.totalTargetCount()).isEqualTo(2);
		assertThat(dues.paidCount()).isEqualTo(1);
		assertThat(dues.unpaidCount()).isEqualTo(1);
	}

	// --- 목록 ---

	@Test
	void 목록은_상태로_거를_수_있고_인원_집계를_함께_준다() {
		Long open = createDues(List.of(member1, member2));
		pay(open, member1);
		closeWithAllPaid();

		var all = duesService.getDuesList(groupId, ownerId, null, PageRequest.of(0, 20));
		var openOnly = duesService.getDuesList(groupId, ownerId, DuesStatus.OPEN, PageRequest.of(0, 20));

		assertThat(all.totalElements()).isEqualTo(2);
		assertThat(openOnly.totalElements()).isEqualTo(1);
		assertThat(openOnly.content().get(0)).satisfies(dues -> {
			assertThat(dues.paidCount()).isEqualTo(1);
			assertThat(dues.unpaidCount()).isEqualTo(1);
			assertThat(dues.targetCount()).isEqualTo(2);
			assertThat(dues.ledgerName()).isEqualTo("운영 장부");
		});
	}

	private DuesCreateRequest createRequest(List<Long> memberIds) {
		return new DuesCreateRequest("2학기 회비", 30_000L, LocalDate.of(2026, 9, 30), memberIds, ledgerId);
	}

	private Long createDues(List<Long> memberIds) {
		return duesService.create(groupId, ownerId, createRequest(memberIds)).duesId();
	}

	private void pay(Long duesId, Long memberId) {
		duesService.changePaymentStatus(duesId, memberId, ownerId, new PaymentStatusUpdateRequest("PAID"));
	}

	private OffsetDateTime paidAtOf(Long duesId, Long memberId) {
		return duesService.getTargets(duesId, ownerId, null, null).stream()
				.filter(target -> target.memberId().equals(memberId))
				.findFirst()
				.orElseThrow()
				.paidAt();
	}

	private Long closeWithAllPaid() {
		Long duesId = createDues(List.of(member1, member2));
		pay(duesId, member1);
		pay(duesId, member2);
		duesService.close(duesId, ownerId);
		return duesId;
	}
}
