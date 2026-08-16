package com.billage.dashboard;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dashboard.dto.DashboardResponse;
import com.billage.entry.ApprovalStatus;
import com.billage.entry.EntryRepository;
import com.billage.entry.EntryType;
import com.billage.ledger.LedgerRepository;
import com.billage.membership.GroupAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * 대시보드 화면용 통합 조회. 집계 테이블 없이 SQL 집계만 사용한다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

	static final int MIN_RECENT_ENTRY_SIZE = 1;
	static final int MAX_RECENT_ENTRY_SIZE = 20;

	private final EntryRepository entryRepository;
	private final LedgerRepository ledgerRepository;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public DashboardResponse getDashboard(Long groupId, Long userId, int recentEntrySize) {
		guard.requireMembership(groupId, userId);
		validateRecentEntrySize(recentEntrySize);

		Map<EntryType, Long> approvedSums = entryRepository.sumApprovedByTypeForGroup(groupId);
		long totalIncome = approvedSums.getOrDefault(EntryType.INCOME, 0L);
		long totalExpense = approvedSums.getOrDefault(EntryType.EXPENSE, 0L);

		DashboardResponse.Summary summary = new DashboardResponse.Summary(
				totalIncome, totalExpense, totalIncome - totalExpense,
				ledgerRepository.countByGroupId(groupId));

		DashboardResponse.Approval approval = new DashboardResponse.Approval(
				entryRepository.countByGroupIdAndStatus(groupId, ApprovalStatus.PENDING));

		List<DashboardResponse.RecentEntry> recentEntries =
				entryRepository.findRecentByGroupId(groupId, PageRequest.of(0, recentEntrySize)).stream()
						.map(DashboardResponse.RecentEntry::from)
						.toList();

		// 회비(Dues) 도메인 구현 시 이 부분을 실제 집계로 교체한다.
		return new DashboardResponse(groupId, summary, approval, DashboardResponse.Dues.EMPTY, recentEntries);
	}

	private void validateRecentEntrySize(int size) {
		if (size < MIN_RECENT_ENTRY_SIZE || size > MAX_RECENT_ENTRY_SIZE) {
			throw new BusinessException(ErrorCode.INVALID_QUERY_PARAMETER,
					"최근 내역 개수는 %d~%d 사이여야 합니다.".formatted(MIN_RECENT_ENTRY_SIZE, MAX_RECENT_ENTRY_SIZE));
		}
	}
}
