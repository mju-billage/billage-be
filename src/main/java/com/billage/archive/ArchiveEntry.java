package com.billage.archive;

import java.time.LocalDate;

import com.billage.entry.ApprovalStatus;
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
 * 보관 시점의 내역 한 건. 승인 대기였던 내역도 그대로 남긴다 — 보관은 그 시점의 사실을 기록하는 것이다.
 * 작성자는 이름만 남긴다(관리자가 나중에 탈퇴해도 기록이 깨지지 않게).
 */
@Entity
@Table(name = "archive_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchiveEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "archive_ledger_id", nullable = false)
	private ArchiveLedger archiveLedger;

	@Enumerated(EnumType.STRING)
	@Column(name = "entry_type", nullable = false, length = 20)
	private EntryType type;

	@Column(nullable = false, length = 20)
	private String title;

	@Column(nullable = false)
	private Long amount;

	@Column(name = "occurred_on", nullable = false)
	private LocalDate occurredOn;

	@Column(length = 30)
	private String memo;

	@Enumerated(EnumType.STRING)
	@Column(name = "approval_status", nullable = false, length = 20)
	private ApprovalStatus approvalStatus;

	@Column(name = "created_by_name", length = 10)
	private String createdByName;

	private ArchiveEntry(EntryType type, String title, Long amount, LocalDate occurredOn, String memo,
			ApprovalStatus approvalStatus, String createdByName) {
		this.type = type;
		this.title = title;
		this.amount = amount;
		this.occurredOn = occurredOn;
		this.memo = memo;
		this.approvalStatus = approvalStatus;
		this.createdByName = createdByName;
	}

	public static ArchiveEntry of(EntryType type, String title, Long amount, LocalDate occurredOn, String memo,
			ApprovalStatus approvalStatus, String createdByName) {
		return new ArchiveEntry(type, title, amount, occurredOn, memo, approvalStatus, createdByName);
	}

	void assignTo(ArchiveLedger archiveLedger) {
		this.archiveLedger = archiveLedger;
	}
}
