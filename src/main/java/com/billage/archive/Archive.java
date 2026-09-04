package com.billage.archive;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 보관된 모임 기록. 보고서와 같은 <b>스냅샷</b>이지만, 보고서가 원본을 남겨 둔 채 복사본을 만드는 것과 달리
 * 보관은 복사한 뒤 원본 폴더·장부·내역을 지워 모임을 비운다(화면 "보관 후 장부는 수정이 불가합니다").
 *
 * <p>제목만 바꿀 수 있고 담긴 내용은 고칠 수 없다. 삭제는 되돌릴 수 없다.
 */
@Entity
@Table(name = "archive")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Archive {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "group_id", nullable = false, updatable = false)
	private Long groupId;

	@Column(nullable = false, length = 20)
	private String title;

	@Column(name = "start_date", nullable = false, updatable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false, updatable = false)
	private LocalDate endDate;

	@Column(name = "total_income", nullable = false, updatable = false)
	private Long totalIncome;

	@Column(name = "total_expense", nullable = false, updatable = false)
	private Long totalExpense;

	@Column(name = "entry_count", nullable = false, updatable = false)
	private Long entryCount;

	@Column(name = "ledger_count", nullable = false, updatable = false)
	private Long ledgerCount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@OneToMany(mappedBy = "archive", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id asc")
	private List<ArchiveLedger> ledgers = new ArrayList<>();

	private Archive(Long groupId, String title, LocalDate startDate, LocalDate endDate,
			long totalIncome, long totalExpense, long entryCount, long ledgerCount) {
		this.groupId = groupId;
		this.title = title;
		this.startDate = startDate;
		this.endDate = endDate;
		this.totalIncome = totalIncome;
		this.totalExpense = totalExpense;
		this.entryCount = entryCount;
		this.ledgerCount = ledgerCount;
	}

	public static Archive of(Long groupId, String title, LocalDate startDate, LocalDate endDate,
			long totalIncome, long totalExpense, long entryCount, long ledgerCount) {
		return new Archive(groupId, title, startDate, endDate, totalIncome, totalExpense, entryCount, ledgerCount);
	}

	public void addLedger(ArchiveLedger ledger) {
		ledgers.add(ledger);
		ledger.assignTo(this);
	}

	public void rename(String title) {
		this.title = title;
	}

	/** 잔액은 저장하지 않고 수입 − 지출로 계산한다(재무 데이터 처리 규칙). */
	public long balance() {
		return totalIncome - totalExpense;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
