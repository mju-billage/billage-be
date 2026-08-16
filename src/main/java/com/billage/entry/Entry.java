package com.billage.entry;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.ledger.Ledger;
import com.billage.membership.GroupMembership;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수입·지출 내역. 금액은 원화 양수이며 유형은 {@link EntryType} 으로 구분한다.
 * 승인(APPROVED)된 내역만 잔액·통계에 반영된다.
 *
 * <p>작성자·승인자는 가입 사용자(User) 기준으로 기록하고, 이름은 그 시점 값을 함께 저장한다.
 */
@Entity
@Table(name = "entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Entry {

	/** 기획 공통 정책상 금액 상한. */
	public static final long MAX_AMOUNT = 999_999_999L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "group_id", nullable = false, updatable = false)
	private Long groupId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ledger_id", nullable = false, updatable = false)
	private Ledger ledger;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
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

	@Column(name = "created_by_user_id", nullable = false, updatable = false)
	private Long createdByUserId;

	@Column(name = "created_by_name", nullable = false, length = 10, updatable = false)
	private String createdByName;

	@Column(name = "approved_by_user_id")
	private Long approvedByUserId;

	@Column(name = "approved_by_name", length = 10)
	private String approvedByName;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Version
	private Long version;

	private Entry(Ledger ledger, EntryType type, String title, Long amount, LocalDate occurredOn, String memo,
			ApprovalStatus approvalStatus, Long createdByUserId, String createdByName) {
		this.groupId = ledger.getGroup().getId();
		this.ledger = ledger;
		this.type = type;
		this.title = title;
		this.amount = amount;
		this.occurredOn = occurredOn;
		this.memo = memo;
		this.approvalStatus = approvalStatus;
		this.createdByUserId = createdByUserId;
		this.createdByName = createdByName;
	}

	/**
	 * 내역 등록. 총무(OWNER)가 등록하면 즉시 승인 상태로, 일반 권한 관리자가 등록하면 승인 대기로 생성된다
	 * (기획 글로벌 정책).
	 */
	public static Entry create(Ledger ledger, GroupMembership author, String authorName, EntryType type,
			String title, Long amount, LocalDate occurredOn, String memo) {
		Entry entry = new Entry(ledger, type, title, amount, occurredOn, memo,
				author.isOwner() ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING,
				author.getUserId(), authorName);
		if (author.isOwner()) {
			entry.approvedByUserId = author.getUserId();
			entry.approvedByName = authorName;
			entry.approvedAt = LocalDateTime.now();
		}
		return entry;
	}

	public boolean isApproved() {
		return this.approvalStatus == ApprovalStatus.APPROVED;
	}

	/**
	 * 총무 승인. 이미 승인된 내역은 다시 승인할 수 없다.
	 * 승인 후에는 수정·삭제해도 승인 대기로 되돌아가지 않는다.
	 */
	public void approve(Long approverUserId, String approverName) {
		if (isApproved()) {
			throw new BusinessException(ErrorCode.ENTRY_ALREADY_APPROVED);
		}
		this.approvalStatus = ApprovalStatus.APPROVED;
		this.approvedByUserId = approverUserId;
		this.approvedByName = approverName;
		this.approvedAt = LocalDateTime.now();
	}

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
