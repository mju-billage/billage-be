package com.billage.archive;

import java.util.ArrayList;
import java.util.List;

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

/** 보관 시점의 장부 하나. 폴더는 이름만 남긴다 — 보관 후 원본 폴더가 사라지기 때문이다. */
@Entity
@Table(name = "archive_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchiveLedger {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "archive_id", nullable = false)
	private Archive archive;

	/** 최상위 영역에 있던 장부는 null 이다. */
	@Column(name = "folder_name", length = 20)
	private String folderName;

	@Column(name = "ledger_name", nullable = false, length = 20)
	private String ledgerName;

	@Column
	private Long budget;

	@Column(name = "total_income", nullable = false)
	private Long totalIncome;

	@Column(name = "total_expense", nullable = false)
	private Long totalExpense;

	@OneToMany(mappedBy = "archiveLedger", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("occurredOn asc, id asc")
	private List<ArchiveEntry> entries = new ArrayList<>();

	private ArchiveLedger(String folderName, String ledgerName, Long budget, long totalIncome, long totalExpense) {
		this.folderName = folderName;
		this.ledgerName = ledgerName;
		this.budget = budget;
		this.totalIncome = totalIncome;
		this.totalExpense = totalExpense;
	}

	public static ArchiveLedger of(String folderName, String ledgerName, Long budget,
			long totalIncome, long totalExpense) {
		return new ArchiveLedger(folderName, ledgerName, budget, totalIncome, totalExpense);
	}

	void assignTo(Archive archive) {
		this.archive = archive;
	}

	public void addEntry(ArchiveEntry entry) {
		entries.add(entry);
		entry.assignTo(this);
	}

	public long balance() {
		return totalIncome - totalExpense;
	}
}
