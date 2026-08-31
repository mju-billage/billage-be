package com.billage.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.billage.entry.EntryType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결산 보고서. 생성 시점의 장부·내역을 복사한 <b>스냅샷</b>이라 이후 원본이 바뀌어도 변하지 않는다.
 * 수정 API 는 없고, 모임이 삭제될 때만 함께 삭제된다.
 *
 * <p>합계는 스냅샷 값이라 컬럼으로 저장하지만 잔액은 저장하지 않고 수입 − 지출로 계산한다.
 */
@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "group_id", nullable = false, updatable = false)
	private Long groupId;

	@Column(nullable = false, length = 20)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "report_type", nullable = false, length = 20)
	private ReportType reportType;

	/** 담긴 내역의 구분. null 이면 전체(수입+지출)다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "entry_type", length = 20)
	private EntryType entryType;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "total_income", nullable = false)
	private Long totalIncome;

	@Column(name = "total_expense", nullable = false)
	private Long totalExpense;

	/** 스냅샷에 담긴 내역 수. 목록에서 자식 테이블을 읽지 않으려고 저장한다. */
	@Column(name = "entry_count", nullable = false)
	private Long entryCount;

	@Column(name = "ledger_count", nullable = false)
	private Long ledgerCount;

	/**
	 * 기간별 보고서의 잔액 흐름. 시작 잔액은 기간 시작일 직전까지의 누적 잔액,
	 * 최종 잔액은 거기에 기간 내 수입·지출을 더한 값이다. 장부별 보고서에는 해당 화면이 없어 null 이다.
	 */
	@Column(name = "opening_balance")
	private Long openingBalance;

	@Column(name = "closing_balance")
	private Long closingBalance;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id asc")
	private List<ReportLedger> ledgers = new ArrayList<>();

	private Report(Long groupId, String title, ReportType reportType, EntryType entryType,
			LocalDate startDate, LocalDate endDate) {
		this.groupId = groupId;
		this.title = title;
		this.reportType = reportType;
		this.entryType = entryType;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	/** 장부 스냅샷들로 보고서를 만든다. 요약 합계는 넘겨받은 스냅샷에서 그대로 계산한다. */
	static Report create(Long groupId, String title, ReportType reportType, EntryType entryType,
			LocalDate startDate, LocalDate endDate, List<ReportLedger> ledgers) {
		Report report = new Report(groupId, title, reportType, entryType, startDate, endDate);
		ledgers.forEach(ledger -> {
			report.ledgers.add(ledger);
			ledger.assignTo(report);
		});
		report.totalIncome = ledgers.stream().mapToLong(ReportLedger::getTotalIncome).sum();
		report.totalExpense = ledgers.stream().mapToLong(ReportLedger::getTotalExpense).sum();
		report.entryCount = ledgers.stream().mapToLong(ReportLedger::getEntryCount).sum();
		report.ledgerCount = (long) ledgers.size();
		return report;
	}

	public long getBalance() {
		return this.totalIncome - this.totalExpense;
	}

	/**
	 * 기간별 보고서의 잔액 흐름을 기록한다. 시작 잔액만 받고 최종 잔액은 스냅샷 합계로 계산한다 —
	 * 두 값을 따로 받으면 서로 어긋난 채 저장될 수 있다.
	 */
	void recordBalanceFlow(long openingBalance) {
		this.openingBalance = openingBalance;
		this.closingBalance = openingBalance + getBalance();
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
