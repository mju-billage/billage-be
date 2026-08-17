package com.billage.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.response.PageResponse;
import com.billage.entry.Entry;
import com.billage.entry.EntryRepository;
import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerRepository;
import com.billage.membership.GroupAccessGuard;
import com.billage.report.dto.ReportCreateRequest;
import com.billage.report.dto.ReportCreateResponse;
import com.billage.report.dto.ReportDetailResponse;
import com.billage.report.dto.ReportSummaryResponse;

import lombok.RequiredArgsConstructor;

/**
 * 결산 보고서. 생성 시점의 장부·내역을 스냅샷으로 복사해 두므로 이후 원본이 바뀌어도 보고서는 변하지 않는다.
 * 수정·삭제 API 는 명세에 없고, 모임이 삭제될 때만 함께 지워진다.
 *
 * <p>권한은 노션 명세상 아직 {@code TBD} 라 제안안(조회 MEMBER / 생성 OWNER)대로 구현했다.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

	private final ReportRepository reportRepository;
	private final ReportLedgerRepository reportLedgerRepository;
	private final LedgerRepository ledgerRepository;
	private final EntryRepository entryRepository;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public PageResponse<ReportSummaryResponse> getReports(Long groupId, Long userId, Pageable pageable) {
		guard.requireMembership(groupId, userId);

		return PageResponse.of(reportRepository.findAllByGroupId(groupId, pageable), ReportSummaryResponse::from);
	}

	/**
	 * 보고서 생성. 선택한 장부의 기간 내 <b>승인된</b> 내역만 스냅샷으로 복사한다.
	 * 기간에 승인된 내역이 하나도 없으면 만들지 않는다.
	 */
	@Transactional
	public ReportCreateResponse create(Long groupId, Long userId, ReportCreateRequest request) {
		guard.requireOwner(groupId, userId);
		if (request.startDate().isAfter(request.endDate())) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "시작일은 종료일보다 늦을 수 없습니다.");
		}

		List<Ledger> ledgers = findLedgers(groupId, request.ledgerIds());
		Map<Long, List<Entry>> entriesByLedger = groupEntriesByLedger(ledgers, request);

		List<ReportLedger> snapshots = ledgers.stream()
				.map(ledger -> ReportLedger.snapshotOf(ledger,
						entriesByLedger.getOrDefault(ledger.getId(), List.of())))
				.toList();
		if (snapshots.stream().mapToInt(ReportLedger::getEntryCount).sum() == 0) {
			throw new BusinessException(ErrorCode.REPORT_RANGE_EMPTY);
		}

		Report report = reportRepository.save(Report.create(groupId, request.title().trim(),
				request.startDate(), request.endDate(), snapshots));

		return ReportCreateResponse.from(report);
	}

	@Transactional(readOnly = true)
	public ReportDetailResponse getDetail(Long reportId, Long userId) {
		Report report = reportRepository.findById(reportId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
		guard.requireMembership(report.getGroupId(), userId);

		return ReportDetailResponse.of(report, reportLedgerRepository.findAllByReportIdWithEntries(reportId));
	}

	/** 요청한 장부를 요청 순서대로 조회한다. 없는 장부·다른 모임 장부는 각각 다른 코드로 구분해 응답한다. */
	private List<Ledger> findLedgers(Long groupId, List<Long> ledgerIds) {
		List<Long> distinctIds = ledgerIds.stream().distinct().toList();
		Map<Long, Ledger> found = new LinkedHashMap<>();
		ledgerRepository.findAllById(distinctIds).forEach(ledger -> found.put(ledger.getId(), ledger));

		return distinctIds.stream()
				.map(ledgerId -> {
					Ledger ledger = found.get(ledgerId);
					if (ledger == null) {
						throw new BusinessException(ErrorCode.LEDGER_NOT_FOUND);
					}
					if (!ledger.getGroup().getId().equals(groupId)) {
						throw new BusinessException(ErrorCode.GROUP_MISMATCH);
					}
					return ledger;
				})
				.toList();
	}

	private Map<Long, List<Entry>> groupEntriesByLedger(List<Ledger> ledgers, ReportCreateRequest request) {
		Map<Long, List<Entry>> entriesByLedger = new LinkedHashMap<>();
		entryRepository.findApprovedInRange(ledgers.stream().map(Ledger::getId).toList(),
						request.startDate(), request.endDate())
				.forEach(entry -> entriesByLedger
						.computeIfAbsent(entry.getLedger().getId(), key -> new ArrayList<>())
						.add(entry));

		return entriesByLedger;
	}
}
