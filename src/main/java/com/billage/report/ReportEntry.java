package com.billage.report;

import java.time.LocalDate;

import com.billage.entry.Entry;
import com.billage.entry.EntryType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보고서에 담긴 내역 한 줄의 스냅샷. 원본 내역이 수정·삭제되어도 이 값은 변하지 않는다.
 * {@code entryId} 는 참고용이며 표시에는 쓰지 않는다.
 */
@Entity
@Table(name = "report_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_ledger_id", nullable = false, updatable = false)
	private ReportLedger reportLedger;

	/** 원본 내역 ID(참고용). 내역이 삭제되어도 스냅샷은 남는다. */
	@Column(name = "entry_id")
	private Long entryId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EntryType type;

	@Column(nullable = false, length = 20)
	private String title;

	@Column(nullable = false)
	private Long amount;

	@Column(name = "occurred_on", nullable = false)
	private LocalDate occurredOn;

	private ReportEntry(Long entryId, EntryType type, String title, Long amount, LocalDate occurredOn) {
		this.entryId = entryId;
		this.type = type;
		this.title = title;
		this.amount = amount;
		this.occurredOn = occurredOn;
	}

	static ReportEntry snapshotOf(Entry entry) {
		return new ReportEntry(entry.getId(), entry.getType(), entry.getTitle(), entry.getAmount(),
				entry.getOccurredOn());
	}

	void assignTo(ReportLedger reportLedger) {
		this.reportLedger = reportLedger;
	}
}
