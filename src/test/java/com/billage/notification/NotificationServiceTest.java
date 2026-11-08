package com.billage.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.billage.auth.jwt.JwtTokenProvider;
import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dashboard.DashboardService;
import com.billage.dues.DuesService;
import com.billage.dues.dto.DuesCreateRequest;
import com.billage.entry.EntryService;
import com.billage.entry.EntryType;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.folder.FolderService;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.group.GroupService;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.member.MemberService;
import com.billage.member.dto.MemberCreateRequest;
import com.billage.membership.GroupMembershipService;
import com.billage.membership.dto.RoleUpdateRequest;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.notification.dto.NotificationResponse;
import com.billage.notification.dto.NotificationSettingUpdateRequest;
import com.billage.support.HttpTestClient;
import com.billage.support.HttpTestClient.Response;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;
import com.billage.user.UserService;
import com.billage.user.WithdrawalReasonType;
import com.billage.user.dto.WithdrawRequest;

/**
 * 앱 안 알림 — 이벤트별 받는 사람, 설정으로 끄기, 읽음, 대시보드 배지, 모임 삭제·탈퇴 정리.
 */
class NotificationServiceTest extends IntegrationTest {

	@LocalServerPort
	int port;

	@Autowired
	NotificationService notificationService;
	@Autowired
	NotificationRepository notificationRepository;
	@Autowired
	GroupService groupService;
	@Autowired
	GroupMembershipService groupMembershipService;
	@Autowired
	FolderService folderService;
	@Autowired
	LedgerService ledgerService;
	@Autowired
	EntryService entryService;
	@Autowired
	DuesService duesService;
	@Autowired
	MemberService memberService;
	@Autowired
	DashboardService dashboardService;
	@Autowired
	UserService userService;
	@Autowired
	UserRepository userRepository;
	@Autowired
	JwtTokenProvider jwtTokenProvider;

	private Long ownerId;
	private Long adminId;
	private Long groupId;
	private Long ledgerId;
	private String invitationCode;

	@BeforeEach
	void setUp() {
		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "encoded", "일반관리자")).getId();
		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
		invitationCode = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(adminId, invitationCode);
		Long folderId = folderService.create(groupId, ownerId, new FolderCreateRequest("폴더", null)).folderId();
		ledgerId = ledgerService.create(folderId, ownerId, new LedgerCreateRequest("운영 장부", null)).ledgerId();
		// 준비 과정에서 생긴 알림(일반관리자 참여)은 지우고 시작한다.
		notificationRepository.deleteAll();
	}

	// --- 이벤트별 받는 사람 ---

	@Test
	void 일반_관리자가_내역을_올리면_총무에게_승인_요청_알림이_가고_총무가_올리면_없다() {
		Long entryId = createEntry(adminId, "대관료");
		createEntry(ownerId, "간식");

		assertThat(notificationsOf(ownerId)).singleElement().satisfies(n -> {
			assertThat(n.category()).isEqualTo(NotificationCategory.APPROVAL);
			assertThat(n.title()).isEqualTo("승인 요청이 도착했어요");
			assertThat(n.body()).isEqualTo("일반관리자님이 대관료 내역을 올렸어요");
			assertThat(n.groupId()).isEqualTo(groupId);
			assertThat(n.targetType()).isEqualTo(NotificationTargetType.ENTRY);
			assertThat(n.targetId()).isEqualTo(entryId);
			assertThat(n.readAt()).isNull();
		});
		assertThat(notificationsOf(adminId)).isEmpty();
	}

	@Test
	void 총무가_승인하면_등록자에게_알림이_간다() {
		Long entryId = createEntry(adminId, "대관료");

		entryService.approve(entryId, ownerId);

		assertThat(notificationsOf(adminId)).singleElement().satisfies(n -> {
			assertThat(n.category()).isEqualTo(NotificationCategory.APPROVAL);
			assertThat(n.body()).isEqualTo("대관료 내역이 승인됐어요");
			assertThat(n.targetId()).isEqualTo(entryId);
		});
	}

	@Test
	void 회비를_만들거나_마감하면_행위자를_뺀_관리자_전원에게_알림이_간다() {
		Long memberId = memberService.addMember(groupId, ownerId,
				new MemberCreateRequest("김모임원", null, null, null)).memberId();
		Long duesId = duesService.create(groupId, ownerId, new DuesCreateRequest("2학기 회비", 30_000L,
				LocalDate.now(), LocalDate.now().plusDays(30), List.of(memberId), ledgerId)).duesId();
		duesService.close(duesId, ownerId);

		assertThat(notificationsOf(adminId)).extracting(NotificationResponse::title)
				.containsExactly("회비가 마감됐어요", "새 회비가 등록됐어요");
		assertThat(notificationsOf(adminId)).allSatisfy(n -> {
			assertThat(n.category()).isEqualTo(NotificationCategory.DUES);
			assertThat(n.targetType()).isEqualTo(NotificationTargetType.DUES);
			assertThat(n.targetId()).isEqualTo(duesId);
		});
		assertThat(notificationsOf(ownerId)).isEmpty();
	}

	@Test
	void 새_관리자가_참여하면_기존_관리자에게_알림이_간다() {
		Long newcomerId = userRepository.save(User.create("new@example.com", "encoded", "새사람")).getId();

		groupMembershipService.join(newcomerId, invitationCode);

		assertThat(notificationsOf(ownerId)).singleElement().satisfies(n -> {
			assertThat(n.category()).isEqualTo(NotificationCategory.GROUP_ACTIVITY);
			assertThat(n.body()).isEqualTo("새사람님이 주리랑에 참여했어요");
			assertThat(n.targetType()).isEqualTo(NotificationTargetType.GROUP);
		});
		assertThat(notificationsOf(adminId)).hasSize(1);
		assertThat(notificationsOf(newcomerId)).isEmpty();
	}

	@Test
	void 총무_권한을_받으면_그_사람에게_내보내지면_그_사람에게_알림이_간다() {
		Long adminMembershipId = membershipIdOf(adminId);

		groupMembershipService.changeRole(groupId, ownerId, adminMembershipId, new RoleUpdateRequest("OWNER"));
		assertThat(notificationsOf(adminId)).singleElement()
				.satisfies(n -> assertThat(n.body()).isEqualTo("주리랑의 총무가 되었어요"));

		groupMembershipService.changeRole(groupId, ownerId, adminMembershipId, new RoleUpdateRequest("MEMBER"));
		groupMembershipService.removeMembership(groupId, ownerId, adminMembershipId);

		assertThat(notificationsOf(adminId)).first().satisfies(n -> {
			assertThat(n.title()).isEqualTo("모임에서 내보내졌어요");
			assertThat(n.targetType()).isNull();
		});
		assertThat(notificationsOf(adminId)).hasSize(2);
	}

	@Test
	void 등록자가_탈퇴한_승인_대기_내역도_승인할_수_있다() {
		Long entryId = createEntry(adminId, "대관료");
		userService.withdraw(adminId, new WithdrawRequest(List.of(),
				List.of(WithdrawalReasonType.NO_LONGER_NEEDED), null));

		entryService.approve(entryId, ownerId);

		assertThat(notificationRepository.findAll()).noneMatch(n -> n.getUserId().equals(adminId));
	}

	@Test
	void 탈퇴하며_총무를_넘기면_넘겨받은_사람에게_알림이_간다() {
		userService.withdraw(ownerId, new WithdrawRequest(
				List.of(new WithdrawRequest.OwnershipTransfer(groupId, adminId)),
				List.of(WithdrawalReasonType.NO_LONGER_NEEDED), null));

		assertThat(notificationsOf(adminId)).singleElement()
				.satisfies(n -> assertThat(n.body()).isEqualTo("주리랑의 총무가 되었어요"));
	}

	// --- 설정 ---

	@Test
	void 꺼_둔_카테고리의_알림은_만들지_않는다() {
		notificationService.updateSettings(ownerId,
				new NotificationSettingUpdateRequest(null, false, null, null, null, null));

		createEntry(adminId, "대관료");

		assertThat(notificationsOf(ownerId)).isEmpty();
	}

	@Test
	void 설정은_기본값이_있고_보낸_값만_바뀌며_마케팅은_가입_동의값과_같다() {
		var defaults = notificationService.getSettings(ownerId);
		assertThat(defaults.groupActivity()).isTrue();
		assertThat(defaults.approval()).isTrue();
		assertThat(defaults.dues()).isTrue();
		assertThat(defaults.noticeAndUpdate()).isTrue();
		assertThat(defaults.marketing()).isFalse();
		assertThat(defaults.nightTime()).isFalse();

		var updated = notificationService.updateSettings(ownerId,
				new NotificationSettingUpdateRequest(false, null, null, null, true, null));

		assertThat(updated.groupActivity()).isFalse();
		assertThat(updated.approval()).isTrue();
		assertThat(updated.marketing()).isTrue();
		assertThat(userRepository.findById(ownerId).orElseThrow().getMarketingAgreedAt()).isNotNull();
		assertThat(notificationService.getSettings(ownerId).groupActivity()).isFalse();

		notificationService.updateSettings(ownerId,
				new NotificationSettingUpdateRequest(null, null, null, null, false, null));
		assertThat(userRepository.findById(ownerId).orElseThrow().getMarketingAgreedAt()).isNull();
	}

	@Test
	void 설정을_동시에_바꿔도_서로의_변경을_덮어쓰지_않는다() throws Exception {
		var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
		var start = new java.util.concurrent.CountDownLatch(1);
		try {
			var first = pool.submit(() -> {
				start.await();
				return notificationService.updateSettings(ownerId,
						new NotificationSettingUpdateRequest(null, false, null, null, null, null));
			});
			var second = pool.submit(() -> {
				start.await();
				return notificationService.updateSettings(ownerId,
						new NotificationSettingUpdateRequest(null, null, false, null, null, null));
			});
			start.countDown();
			first.get();
			second.get();
		} finally {
			pool.shutdown();
		}

		var settings = notificationService.getSettings(ownerId);
		assertThat(settings.approval()).isFalse();
		assertThat(settings.dues()).isFalse();
	}

	// --- 읽음·배지 ---

	@Test
	void 읽음_처리는_본인_것만_되고_대시보드_배지에_반영된다() {
		createEntry(adminId, "대관료");
		Long notificationId = notificationsOf(ownerId).get(0).notificationId();
		assertThat(dashboardService.getDashboard(groupId, ownerId, 5).hasUnreadNotification()).isTrue();
		assertThat(dashboardService.getDashboard(groupId, adminId, 5).hasUnreadNotification()).isFalse();

		assertThatThrownBy(() -> notificationService.markRead(notificationId, adminId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);

		notificationService.markRead(notificationId, ownerId);
		var readAt = notificationsOf(ownerId).get(0).readAt();
		notificationService.markRead(notificationId, ownerId);

		assertThat(readAt).isNotNull();
		assertThat(notificationsOf(ownerId).get(0).readAt()).isEqualTo(readAt);
		assertThat(dashboardService.getDashboard(groupId, ownerId, 5).hasUnreadNotification()).isFalse();
	}

	// --- 정리 ---

	@Test
	void 모임을_삭제하면_그_모임_알림이_지워지고_탈퇴하면_내_알림이_지워진다() {
		createEntry(adminId, "대관료");
		notificationService.updateSettings(adminId,
				new NotificationSettingUpdateRequest(null, null, null, null, null, true));
		Long otherGroupId = groupService.create(adminId, new GroupCreateRequest("다른모임", null, null)).groupId();
		groupMembershipService.join(ownerId,
				groupMembershipService.createInvitation(otherGroupId, adminId).invitationCode());

		groupService.delete(groupId, ownerId);
		assertThat(notificationRepository.findAll()).extracting(Notification::getGroupId)
				.containsOnly(otherGroupId);

		userService.withdraw(adminId, new WithdrawRequest(
				List.of(new WithdrawRequest.OwnershipTransfer(otherGroupId, ownerId)),
				List.of(WithdrawalReasonType.NO_LONGER_NEEDED), null));
		assertThat(notificationRepository.findAll()).noneMatch(n -> n.getUserId().equals(adminId));
	}

	// --- HTTP ---

	@Test
	void HTTP_로_목록을_받고_읽음은_204() {
		createEntry(adminId, "대관료");
		HttpTestClient http = new HttpTestClient(port);
		String token = jwtTokenProvider.createAccessToken(ownerId);

		Response list = http.get("/api/v1/notifications", token);
		assertThat(list.status()).isEqualTo(200);
		List<Map<String, Object>> content = (List<Map<String, Object>>) list.at("data.content");
		assertThat(content).singleElement().satisfies(n -> assertThat(n)
				.containsKeys("notificationId", "category", "title", "body", "groupId", "targetType", "targetId",
						"readAt", "createdAt"));

		Response read = http.patchJson("/api/v1/notifications/" + content.get(0).get("notificationId") + "/read",
				Map.of(), token);
		assertThat(read.status()).isEqualTo(204);

		Response settings = http.patchJson("/api/v1/notifications/settings", Map.of("dues", false), token);
		assertThat(settings.status()).isEqualTo(200);
		assertThat(settings.at("data.dues")).isEqualTo(false);
		assertThat(settings.at("data.approval")).isEqualTo(true);
	}

	private Long createEntry(Long userId, String title) {
		return entryService.create(ledgerId, userId, new EntryCreateRequest(EntryType.EXPENSE, title, 10_000L,
				LocalDate.of(2026, 11, 1), null, null, null)).entryId();
	}

	private List<NotificationResponse> notificationsOf(Long userId) {
		return notificationService.getNotifications(userId,
				PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt", "id"))).content();
	}

	private Long membershipIdOf(Long userId) {
		return groupMembershipService.getMemberships(groupId, ownerId).stream()
				.filter(m -> m.userId().equals(userId)).findFirst().orElseThrow().membershipId();
	}
}
