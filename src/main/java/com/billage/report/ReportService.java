package com.billage.report;

import java.time.LocalDate;
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
import com.billage.entry.EntryType;
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
	public PageResponse<ReportSummaryResponse> getReports(Long groupId, Long userId, ReportType reportType,
			Pageable pageable) {
		guard.requireMembership(groupId, userId);

		return PageResponse.of(reportRepository.search(groupId, reportType, pageable),
				ReportSummaryResponse::from);
	}

	/**
	 * 보고서 생성. 승인된 내역만 스냅샷으로 복사하며, 담을 내역이 하나도 없으면 만들지 않는다.
	 *
	 * <p>유형에 따라 무엇을 받고 무엇을 서버가 정하는지가 다르다.
	 * <ul>
	 *   <li><b>장부별</b> — 장부와 구분을 받고 <b>기간은 서버가 정한다</b>(담긴 내역의 실제 최소~최대 발생일).
	 *       화면에 기간 입력이 없기 때문이다.</li>
	 *   <li><b>기간별</b> — 기간을 받고 <b>장부는 서버가 고른다</b>(그 기간에 승인 내역이 있는 장부 전부).
	 *       화면에 장부 선택이 없기 때문이다.</li>
	 * </ul>
	 */
	@Transactional
	public ReportCreateResponse create(Long groupId, Long userId, ReportCreateRequest request) {
		guard.requireOwner(groupId, userId);
		String title = requireNonBlank(request.title());

		return request.reportType() == ReportType.BY_LEDGER
				? createByLedger(groupId, title, request)
				: createByPeriod(groupId, title, request);
	}

	private ReportCreateResponse createByLedger(Long groupId, String title, ReportCreateRequest request) {
		if (request.ledgerIds() == null || request.ledgerIds().isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "장부를 1개 이상 선택해야 합니다.");
		}

		List<Ledger> ledgers = findLedgers(groupId, request.ledgerIds());
		List<Long> ledgerIds = ledgers.stream().map(Ledger::getId).toList();
		List<Entry> entries = entryRepository.findApprovedForReport(ledgerIds, request.entryType(), null, null);
		if (entries.isEmpty()) {
			throw new BusinessException(ErrorCode.REPORT_RANGE_EMPTY);
		}

		// 기간 입력이 없는 화면이라, 담긴 내역이 실제로 걸쳐 있는 범위를 기간으로 삼는다.
		LocalDate startDate = entries.get(0).getOccurredOn();
		LocalDate endDate = entries.stream().map(Entry::getOccurredOn).max(LocalDate::compareTo).orElseThrow();

		Report report = reportRepository.save(Report.create(groupId, title, ReportType.BY_LEDGER,
				request.entryType(), startDate, endDate, snapshotsOf(ledgers, entries)));

		return ReportCreateResponse.from(report);
	}

	private ReportCreateResponse createByPeriod(Long groupId, String title, ReportCreateRequest request) {
		if (request.startDate() == null || request.endDate() == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "조회 기간은 필수입니다.");
		}
		if (request.startDate().isAfter(request.endDate())) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "시작일은 종료일보다 늦을 수 없습니다.");
		}

		List<Long> ledgerIds = entryRepository.findLedgerIdsWithApprovedEntries(groupId,
				request.startDate(), request.endDate());
		if (ledgerIds.isEmpty()) {
			throw new BusinessException(ErrorCode.REPORT_RANGE_EMPTY);
		}

		List<Ledger> ledgers = ledgerRepository.findAllById(ledgerIds);
		List<Entry> entries = entryRepository.findApprovedForReport(ledgerIds, null,
				request.startDate(), request.endDate());

		Report report = Report.create(groupId, title, ReportType.BY_PERIOD, null,
				request.startDate(), request.endDate(), snapshotsOf(ledgers, entries));
		// 잔액 흐름 카드는 기간 '직전'까지의 누적 잔액에서 출발한다.
		Map<EntryType, Long> before = entryRepository.sumApprovedBefore(ledgerIds, request.startDate());
		report.recordBalanceFlow(before.getOrDefault(EntryType.INCOME, 0L)
				- before.getOrDefault(EntryType.EXPENSE, 0L));

		return ReportCreateResponse.from(reportRepository.save(report));
	}

	private List<ReportLedger> snapshotsOf(List<Ledger> ledgers, List<Entry> entries) {
		Map<Long, List<Entry>> byLedger = new LinkedHashMap<>();
		entries.forEach(entry -> byLedger
				.computeIfAbsent(entry.getLedger().getId(), key -> new ArrayList<>())
				.add(entry));

		return ledgers.stream()
				.map(ledger -> ReportLedger.snapshotOf(ledger, byLedger.getOrDefault(ledger.getId(), List.of())))
				.toList();
	}

	@Transactional(readOnly = true)
	public ReportDetailResponse getDetail(Long reportId, Long userId) {
		Report report = reportRepository.findById(reportId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
		guard.requireMembership(report.getGroupId(), userId);

		return ReportDetailResponse.of(report, reportLedgerRepository.findAllByReportIdWithEntries(reportId));
	}

	/**
	 * 공백 전용 보고서 제목 차단. `@NotBlank` 는 컨트롤러 경로에만 적용되므로 Service 에서도 막는다.
	 */
	private String requireNonBlank(String title) {
		String trimmed = title.trim();
		if (trimmed.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "보고서 제목은 공백일 수 없습니다.");
		}
		return trimmed;
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

}
