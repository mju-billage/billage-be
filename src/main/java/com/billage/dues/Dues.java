package com.billage.dues;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.member.Member;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회비 한 회차. 1인당 금액({@code amount})은 생성 후 바꿀 수 없고, 총액은 저장하지 않고
 * <b>납부 완료 인원 × 금액</b>으로 계산한다 — 대상자를 빼면 총액이 달라지는 것이 정상 동작이다.
 *
 * <p>마감하면 선택한 장부에 수입 내역 1건을 만들고 그 ID 를 {@code generatedEntryId} 로 들고 있지만,
 * <b>마감 즉시 회비와 내역은 서로 독립된 데이터</b>다(기획 확정). 회비를 지워도 내역은 장부에 남고,
 * 내역이 지워져도 회비는 남는다. 그래서 이 참조에는 FK 를 걸지 않는다.
 */
@Entity
@Table(name = "dues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dues {

	/** 기획 공통 정책상 금액 상한. */
	public static final long MAX_AMOUNT = 999_999_999L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "group_id", nullable = false, updatable = false)
	private Long groupId;

	@Column(nullable = false, length = 20)
	private String title;

	@Column(nullable = false, updatable = false)
	private Long amount;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DuesStatus status;

	/** 마감 시 수입 내역을 만들 장부. 장부가 삭제될 수 있어 참조만 들고 있는다. */
	@Column(name = "ledger_id")
	private Long ledgerId;

	@Column(name = "generated_entry_id")
	private Long generatedEntryId;

	@Column(name = "closed_at")
	private LocalDateTime closedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Version
	private Long version;

	@OneToMany(mappedBy = "dues", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id asc")
	private List<DuesMember> targets = new ArrayList<>();

	private Dues(Long groupId, String title, Long amount, LocalDate dueDate, Long ledgerId) {
		this.groupId = groupId;
		this.title = title;
		this.amount = amount;
		this.dueDate = dueDate;
		this.ledgerId = ledgerId;
		this.status = DuesStatus.OPEN;
	}

	public static Dues create(Long groupId, String title, Long amount, LocalDate dueDate, Long ledgerId,
			List<Member> targets) {
		Dues dues = new Dues(groupId, title, amount, dueDate, ledgerId);
		targets.forEach(dues::addTarget);
		return dues;
	}

	private void addTarget(Member member) {
		this.targets.add(DuesMember.of(this, member));
	}

	public boolean isClosed() {
		return this.status == DuesStatus.CLOSED;
	}

	/** 마감된 회비는 어떤 값도 바꿀 수 없다. */
	public void requireOpen() {
		if (isClosed()) {
			throw new BusinessException(ErrorCode.DUES_ALREADY_CLOSED);
		}
	}

	public void updateTitle(String title) {
		requireOpen();
		this.title = title;
	}

	public void updateDueDate(LocalDate dueDate) {
		requireOpen();
		this.dueDate = dueDate;
	}

	public void moveToLedger(Long ledgerId) {
		requireOpen();
		this.ledgerId = ledgerId;
	}

	/**
	 * 납부 대상 교체. 빠진 대상자는 납부 여부와 무관하게 제거한다 — 총액이 달라지는 것이 정상이며
	 * 보정은 총무가 수기로 한다(기획 확정). 이미 있는 대상은 납부 상태를 유지한다.
	 */
	public void replaceTargets(List<Member> members) {
		requireOpen();
		List<Long> keepIds = members.stream().map(Member::getId).toList();
		this.targets.removeIf(target -> !keepIds.contains(target.getMember().getId()));

		List<Long> existingIds = this.targets.stream().map(target -> target.getMember().getId()).toList();
		members.stream()
				.filter(member -> !existingIds.contains(member.getId()))
				.forEach(this::addTarget);
	}

	public void changePaymentStatus(Member member, PaymentStatus status) {
		requireOpen();
		this.targets.stream()
				.filter(target -> target.getMember().getId().equals(member.getId()))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND))
				.changeStatus(status);
	}

	public long paidCount() {
		return this.targets.stream().filter(DuesMember::isPaid).count();
	}

	public long targetCount() {
		return this.targets.size();
	}

	/** 실제로 걷힌 금액. 저장하지 않고 납부 완료 인원 × 1인당 금액으로 계산한다. */
	public long totalCollectedAmount() {
		return paidCount() * this.amount;
	}

	/** 대상 전원이 납부해야 마감할 수 있다. */
	public void close(Long generatedEntryId) {
		requireOpen();
		if (paidCount() != targetCount()) {
			throw new BusinessException(ErrorCode.UNPAID_MEMBER_EXISTS);
		}
		this.status = DuesStatus.CLOSED;
		this.generatedEntryId = generatedEntryId;
		this.closedAt = LocalDateTime.now();
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
