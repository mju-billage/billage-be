package com.billage.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.response.KoreanTime;
import com.billage.entry.EntryRepository;
import com.billage.entry.EntryType;
import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerRepository;
import com.billage.ledger.LedgerStats;
import com.billage.membership.GroupAccessGuard;
import com.billage.statistics.dto.StatisticsResponse;

import lombok.RequiredArgsConstructor;

/**
 * 「통계/분석」 화면용 집계. 저장하는 값 없이 매번 계산한다.
 *
 * <p>모든 수치는 <b>승인된 내역만</b> 반영한다 — 잔액·보고서와 같은 규칙이다.
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

	/** 활성 장부 판정 기간. 화면명세 "최근 7일간 {N}건의 내역이 추가됨". */
	private static final int ACTIVE_WINDOW_DAYS = 7;

	/** 지출 비중 차트에 개별로 올리는 장부 수. 나머지는 '기타'로 묶는다. */
	private static final int EXPENSE_SHARE_TOP_N = 4;

	private final LedgerRepository ledgerRepository;
	private final EntryRepository entryRepository;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public StatisticsResponse getStatistics(Long groupId, Long userId) {
		guard.requireMembership(groupId, userId);

		List<Ledger> ledgers = ledgerRepository.findAllInGroup(groupId, null);
		if (ledgers.isEmpty()) {
			return new StatisticsResponse(null, List.of(),
					new StatisticsResponse.ExpenseShare(0L, List.of()));
		}

		List<Long> ledgerIds = ledgers.stream().map(Ledger::getId).toList();
		Map<Long, Map<EntryType, Long>> sums = entryRepository.sumApprovedByLedger(ledgerIds);
		Map<Long, Long> entryCounts = entryRepository.countByLedgers(ledgerIds);

		return new StatisticsResponse(
				mostActiveLedger(groupId, ledgers, sums, entryCounts),
				budgetUsage(ledgers, sums),
				expenseShare(ledgers, sums));
	}

	/**
	 * 최근 {@value #ACTIVE_WINDOW_DAYS}일 안에 내역이 가장 많이 등록된 장부.
	 * 그 기간에 등록된 내역이 하나도 없으면 보여 줄 게 없으므로 null 이다.
	 */
	private StatisticsResponse.MostActiveLedger mostActiveLedger(Long groupId, List<Ledger> ledgers,
			Map<Long, Map<EntryType, Long>> sums, Map<Long, Long> entryCounts) {
		List<Object[]> recent = entryRepository.countRecentByLedger(groupId,
				LocalDate.now(KoreanTime.ZONE).minusDays(ACTIVE_WINDOW_DAYS).atStartOfDay());
		if (recent.isEmpty()) {
			return null;
		}

		Long ledgerId = (Long) recent.get(0)[0];
		long recentCount = (Long) recent.get(0)[1];
		Ledger ledger = ledgers.stream()
				.filter(candidate -> candidate.getId().equals(ledgerId))
				.findFirst()
				.orElse(null);
		if (ledger == null) {
			// 집계 사이에 장부가 지워진 경우. 카드를 비우는 편이 깨진 값을 보여 주는 것보다 낫다.
			return null;
		}

		LedgerStats stats = statsOf(ledger, sums, entryCounts);
		return new StatisticsResponse.MostActiveLedger(ledger.getId(), ledger.getName(), recentCount,
				stats.totalIncome(), stats.totalExpense(), ledger.getBudget(),
				stats.budgetUsageRate(ledger.getBudget()));
	}

	/** 예산이 설정된 장부만, 소진율 내림차순. */
	private List<StatisticsResponse.BudgetUsage> budgetUsage(List<Ledger> ledgers,
			Map<Long, Map<EntryType, Long>> sums) {
		return ledgers.stream()
				.filter(ledger -> ledger.getBudget() != null)
				.map(ledger -> {
					long expense = sumOf(sums, ledger.getId(), EntryType.EXPENSE);
					return new StatisticsResponse.BudgetUsage(ledger.getId(), ledger.getName(), ledger.getBudget(),
							expense, new LedgerStats(0L, expense, 0L).budgetUsageRate(ledger.getBudget()));
				})
				.filter(usage -> usage.budgetUsageRate() != null)
				.sorted(Comparator.comparing(StatisticsResponse.BudgetUsage::budgetUsageRate).reversed())
				.toList();
	}

	/**
	 * 지출 비중. 상위 {@value #EXPENSE_SHARE_TOP_N}개만 개별로 두고 나머지는 '기타'로 합친다.
	 * 지출이 0인 장부는 차트에 조각이 생기지 않으므로 뺀다.
	 */
	private StatisticsResponse.ExpenseShare expenseShare(List<Ledger> ledgers,
			Map<Long, Map<EntryType, Long>> sums) {
		record Row(Long ledgerId, String name, long expense) {
		}

		List<Row> rows = ledgers.stream()
				.map(ledger -> new Row(ledger.getId(), ledger.getName(), sumOf(sums, ledger.getId(),
						EntryType.EXPENSE)))
				.filter(row -> row.expense() > 0)
				.sorted(Comparator.comparingLong(Row::expense).reversed())
				.toList();

		long total = rows.stream().mapToLong(Row::expense).sum();
		if (total == 0) {
			return new StatisticsResponse.ExpenseShare(0L, List.of());
		}

		List<StatisticsResponse.ExpenseShare.Item> items = new java.util.ArrayList<>(
				rows.stream().limit(EXPENSE_SHARE_TOP_N)
						.map(row -> new StatisticsResponse.ExpenseShare.Item(row.ledgerId(), row.name(),
								row.expense(), share(row.expense(), total)))
						.toList());

		long others = rows.stream().skip(EXPENSE_SHARE_TOP_N).mapToLong(Row::expense).sum();
		if (others > 0) {
			items.add(new StatisticsResponse.ExpenseShare.Item(null, "기타", others, share(others, total)));
		}

		return new StatisticsResponse.ExpenseShare(total, items);
	}

	private BigDecimal share(long amount, long total) {
		return BigDecimal.valueOf(amount)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
	}

	private LedgerStats statsOf(Ledger ledger, Map<Long, Map<EntryType, Long>> sums, Map<Long, Long> entryCounts) {
		return new LedgerStats(sumOf(sums, ledger.getId(), EntryType.INCOME),
				sumOf(sums, ledger.getId(), EntryType.EXPENSE),
				entryCounts.getOrDefault(ledger.getId(), 0L));
	}

	private long sumOf(Map<Long, Map<EntryType, Long>> sums, Long ledgerId, EntryType type) {
		return sums.getOrDefault(ledgerId, Map.of()).getOrDefault(type, 0L);
	}
}
