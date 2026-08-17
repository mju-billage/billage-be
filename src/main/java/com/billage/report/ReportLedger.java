package com.billage.report;

import java.util.ArrayList;
import java.util.List;

import com.billage.entry.Entry;
import com.billage.entry.EntryType;
import com.billage.ledger.Ledger;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보고서에 담긴 장부 한 개의 스냅샷. 합계는 선택 기간 안의 <b>승인된</b> 내역만 반영한다.
 * 내역 스냅샷은 이 장부에 완전히 종속되므로 cascade + orphanRemoval 로 함께 관리한다.
 */
@Entity
@Table(name = "report_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportLedger {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false, updatable = false)
	private Report report;

	/** 원본 장부 ID(참고용). 장부가 삭제되어도 스냅샷은 남는다. */
	@Column(name = "ledger_id")
	private Long ledgerId;

	@Column(name = "ledger_name", nullable = false, length = 20)
	private String ledgerName;

	@Column(name = "total_income", nullable = false)
	private Long totalIncome;

	@Column(name = "total_expense", nullable = false)
	private Long totalExpense;

	@OneToMany(mappedBy = "reportLedger", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("occurredOn asc, id asc")
	private List<ReportEntry> entries = new ArrayList<>();

	private ReportLedger(Long ledgerId, String ledgerName, Long totalIncome, Long totalExpense) {
		this.ledgerId = ledgerId;
		this.ledgerName = ledgerName;
		this.totalIncome = totalIncome;
		this.totalExpense = totalExpense;
	}

	/** 선택 기간 안의 승인된 내역만 넘겨받아 장부 스냅샷을 만든다. 기간에 내역이 없는 장부도 0원으로 포함된다. */
	static ReportLedger snapshotOf(Ledger ledger, List<Entry> approvedEntries) {
		ReportLedger snapshot = new ReportLedger(ledger.getId(), ledger.getName(),
				sumOf(approvedEntries, EntryType.INCOME), sumOf(approvedEntries, EntryType.EXPENSE));
		approvedEntries.forEach(entry -> snapshot.add(ReportEntry.snapshotOf(entry)));
		return snapshot;
	}

	private static long sumOf(List<Entry> entries, EntryType type) {
		return entries.stream().filter(entry -> entry.getType() == type).mapToLong(Entry::getAmount).sum();
	}

	private void add(ReportEntry entry) {
		this.entries.add(entry);
		entry.assignTo(this);
	}

	void assignTo(Report report) {
		this.report = report;
	}

	public long getBalance() {
		return this.totalIncome - this.totalExpense;
	}

	public int getEntryCount() {
		return this.entries.size();
	}
}
