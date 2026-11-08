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
 * {@code entryId} 는 표시에는 쓰지 않고, 상세에서 원본 내역의 <b>현재</b> 증빙을 찾을 때만 쓴다.
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

	/** 생성 시점의 원본 메모. V23 이전에 만든 보고서는 null 이다. */
	@Column(length = 30)
	private String memo;

	private ReportEntry(Long entryId, EntryType type, String title, Long amount, LocalDate occurredOn,
			String memo) {
		this.entryId = entryId;
		this.type = type;
		this.title = title;
		this.amount = amount;
		this.occurredOn = occurredOn;
		this.memo = memo;
	}

	static ReportEntry snapshotOf(Entry entry) {
		return new ReportEntry(entry.getId(), entry.getType(), entry.getTitle(), entry.getAmount(),
				entry.getOccurredOn(), entry.getMemo());
	}

	void assignTo(ReportLedger reportLedger) {
		this.reportLedger = reportLedger;
	}
}
