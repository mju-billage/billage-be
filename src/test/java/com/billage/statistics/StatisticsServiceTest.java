package com.billage.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.response.KoreanTime;
import com.billage.entry.EntryService;
import com.billage.entry.EntryType;
import com.billage.entry.dto.EntryCreateRequest;
import com.billage.folder.FolderService;
import com.billage.folder.dto.FolderCreateRequest;
import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.ledger.LedgerService;
import com.billage.ledger.dto.LedgerCreateRequest;
import com.billage.membership.GroupMembershipService;
import com.billage.statistics.dto.StatisticsResponse;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 통계/분석 — 활성 장부 판정, 예산 소진율 정렬, 지출 비중의 '기타' 묶기.
 */
class StatisticsServiceTest extends IntegrationTest {

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
	StatisticsService statisticsService;
	@Autowired
	UserRepository userRepository;
	@Autowired
	JdbcTemplate jdbcTemplate;

	private Long ownerId;
	private Long adminId;
	private Long outsiderId;
	private Long groupId;
	private Long folderId;

	@BeforeEach
	void setUp() {
		ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		adminId = userRepository.save(User.create("admin@example.com", "encoded", "일반관리자")).getId();
		outsiderId = userRepository.save(User.create("outsider@example.com", "encoded", "남의모임")).getId();

		groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();
		String code = groupMembershipService.createInvitation(groupId, ownerId).invitationCode();
		groupMembershipService.join(adminId, code);
		folderId = folderService.create(groupId, ownerId, new FolderCreateRequest("2026", null)).folderId();
	}

	@Test
	void 장부가_없으면_모든_블록이_비어_있다() {
		StatisticsResponse stats = statisticsService.getStatistics(groupId, ownerId);

		assertThat(stats.mostActiveLedger()).isNull();
		assertThat(stats.budgetUsage()).isEmpty();
		assertThat(stats.expenseShare().items()).isEmpty();
		assertThat(stats.expenseShare().totalExpense()).isZero();
	}

	@Test
	void 최근_등록_건수가_가장_많은_장부를_활성_장부로_뽑는다() {
		Long busy = ledger("MT", 1_000_000L);
		Long quiet = ledger("비품", null);
		expense(busy, "숙소비", 300_000L);
		expense(busy, "식비", 200_000L);
		expense(quiet, "프린터", 100_000L);

		var active = statisticsService.getStatistics(groupId, ownerId).mostActiveLedger();

		assertThat(active.ledgerId()).isEqualTo(busy);
		assertThat(active.recentEntryCount()).isEqualTo(2);
		assertThat(active.totalExpense()).isEqualTo(500_000L);
		assertThat(active.budgetUsageRate()).isEqualByComparingTo("50.00");
	}

	@Test
	void 승인_대기_내역은_활성_판정에도_집계에도_들어가지_않는다() {
		Long ledgerId = ledger("MT", null);
		entryService.create(ledgerId, adminId, new EntryCreateRequest(EntryType.EXPENSE, "승인 대기", 900_000L,
				LocalDate.of(2026, 7, 20), null, null, null));

		StatisticsResponse stats = statisticsService.getStatistics(groupId, ownerId);

		assertThat(stats.mostActiveLedger()).isNull();
		assertThat(stats.expenseShare().totalExpense()).isZero();
	}

	@Test
	void 예산_대비_소비는_예산이_있는_장부만_소진율_내림차순으로_준다() {
		Long high = ledger("높은 소진", 1_000_000L);
		Long low = ledger("낮은 소진", 1_000_000L);
		ledger("예산 없음", null);
		expense(high, "지출", 800_000L);
		expense(low, "지출", 100_000L);

		var usage = statisticsService.getStatistics(groupId, ownerId).budgetUsage();

		assertThat(usage).hasSize(2);
		assertThat(usage.get(0).ledgerId()).isEqualTo(high);
		assertThat(usage.get(0).budgetUsageRate()).isEqualByComparingTo("80.00");
		assertThat(usage.get(1).ledgerId()).isEqualTo(low);
	}

	@Test
	void 지출_비중은_상위_넷만_남기고_나머지를_기타로_묶는다() {
		for (int i = 1; i <= 5; i++) {
			expense(ledger("장부" + i, null), "지출", 100_000L * (6 - i));
		}

		var share = statisticsService.getStatistics(groupId, ownerId).expenseShare();

		// 500+400+300+200+100 = 1,500
		assertThat(share.totalExpense()).isEqualTo(1_500_000L);
		assertThat(share.items()).hasSize(5);
		assertThat(share.items().get(0).name()).isEqualTo("장부1");
		assertThat(share.items().get(0).share()).isEqualByComparingTo("33.33");

		var others = share.items().get(4);
		assertThat(others.name()).isEqualTo("기타");
		// 5위 이하는 하나뿐이라 그 금액이 그대로 '기타'가 된다.
		assertThat(others.ledgerId()).isNull();
		assertThat(others.totalExpense()).isEqualTo(100_000L);
	}

	@Test
	void 장부가_넷_이하면_기타를_만들지_않는다() {
		expense(ledger("MT", null), "지출", 100_000L);
		expense(ledger("비품", null), "지출", 50_000L);

		var share = statisticsService.getStatistics(groupId, ownerId).expenseShare();

		assertThat(share.items()).hasSize(2);
		assertThat(share.items()).noneMatch(item -> item.ledgerId() == null);
	}

	@Test
	void 지출이_없는_장부는_차트에_들어가지_않는다() {
		Long spent = ledger("MT", null);
		ledger("빈 장부", null);
		expense(spent, "지출", 100_000L);
		// 수입만 있는 장부도 지출 차트에는 조각이 없다.
		Long incomeOnly = ledger("후원", null);
		entryService.create(incomeOnly, ownerId, new EntryCreateRequest(EntryType.INCOME, "후원금", 500_000L,
				LocalDate.of(2026, 7, 20), null, null, null));

		var share = statisticsService.getStatistics(groupId, ownerId).expenseShare();

		assertThat(share.items()).singleElement()
				.satisfies(item -> assertThat(item.name()).isEqualTo("MT"));
	}

	@Test
	void 다른_모임_사람은_통계를_볼_수_없다() {
		assertThatThrownBy(() -> statisticsService.getStatistics(groupId, outsiderId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ACCESS_DENIED);
	}

	@Test
	void 활성_판정은_오늘_포함_7일까지만_본다() {
		Long recent = ledger("최근", null);
		Long old = ledger("예전", null);
		// '예전' 장부에 더 많이 넣되 전부 창 밖으로 밀어 둔다. 창을 하루라도 넓게 잡으면 이쪽이 뽑힌다.
		expense(recent, "6일 전 지출", 100_000L);
		backdateEntries(recent, 6);
		expense(old, "7일 전 지출 1", 100_000L);
		expense(old, "7일 전 지출 2", 100_000L);
		backdateEntries(old, 7);

		var active = statisticsService.getStatistics(groupId, ownerId).mostActiveLedger();

		assertThat(active.ledgerId()).isEqualTo(recent);
		assertThat(active.recentEntryCount()).isEqualTo(1);
	}

	@Test
	void 창_밖의_내역만_있으면_활성_장부가_없다() {
		Long ledgerId = ledger("예전", null);
		expense(ledgerId, "7일 전 지출", 100_000L);
		backdateEntries(ledgerId, 7);

		assertThat(statisticsService.getStatistics(groupId, ownerId).mostActiveLedger()).isNull();
	}

	/**
	 * 등록 시각을 과거로 돌린다. {@code createdAt} 은 저장 시점에 채워져 테스트에서 지정할 수 없으므로
	 * 저장한 뒤 직접 고친다. 활성 장부 판정이 이 값을 보기 때문에 경계를 검증하려면 필요하다.
	 */
	private void backdateEntries(Long ledgerId, int days) {
		jdbcTemplate.update("update entry set created_at = ? where ledger_id = ?",
				LocalDate.now(KoreanTime.ZONE).minusDays(days).atTime(12, 0), ledgerId);
	}

	private Long ledger(String name, Long budget) {
		return ledgerService.create(folderId, ownerId, new LedgerCreateRequest(name, budget)).ledgerId();
	}

	private void expense(Long ledgerId, String title, long amount) {
		entryService.create(ledgerId, ownerId, new EntryCreateRequest(EntryType.EXPENSE, title, amount,
				LocalDate.of(2026, 7, 20), null, null, null));
	}
}
