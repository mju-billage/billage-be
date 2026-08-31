package com.billage.dues;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.response.KoreanTime;
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

	/** 납부 시작일. 오늘이 이 날짜 전이면 화면에서 '납부 예정'으로 보인다({@link #phase()}). */
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

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

	private Dues(Long groupId, String title, Long amount, LocalDate startDate, LocalDate dueDate, Long ledgerId) {
		this.groupId = groupId;
		this.title = title;
		this.amount = amount;
		this.startDate = startDate;
		this.dueDate = dueDate;
		this.ledgerId = ledgerId;
		this.status = DuesStatus.OPEN;
	}

	public static Dues create(Long groupId, String title, Long amount, LocalDate startDate, LocalDate dueDate,
			Long ledgerId, List<Member> targets) {
		Dues dues = new Dues(groupId, title, amount, startDate, dueDate, ledgerId);
		targets.forEach(dues::addTarget);
		return dues;
	}

	/**
	 * 화면에 보여 줄 상태. 저장된 {@code status} 는 OPEN|CLOSED 뿐이고, 시작일 전이면 SCHEDULED 로 파생한다.
	 */
	public DuesStatus phase() {
		// 서버 타임존이 아니라 업무 기준 시간대(Asia/Seoul)로 오늘을 정한다. UTC 로 뜬 서버에서
		// LocalDate.now() 를 쓰면 한국 시간 00~09시 사이에 하루 전 날짜가 나와, 오늘 시작하는 회비가
		// 오전 내내 '납부 예정'으로 보인다.
		return DuesStatus.phaseOf(this.status, this.startDate, LocalDate.now(KoreanTime.ZONE));
	}

	/** 시작일 전이면 아직 걷기 시작하지 않았으므로 납부 상태를 바꿀 수 없다. */
	public void requireStarted() {
		if (phase() == DuesStatus.SCHEDULED) {
			throw new BusinessException(ErrorCode.DUES_NOT_STARTED);
		}
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

	/** 기간은 시작일과 마감일을 함께 바꾼다. 화면이 달력에서 범위로 골라 오기 때문이다. */
	public void updatePeriod(LocalDate startDate, LocalDate dueDate) {
		requireOpen();
		LocalDate newStart = startDate != null ? startDate : this.startDate;
		LocalDate newDue = dueDate != null ? dueDate : this.dueDate;
		if (newStart.isAfter(newDue)) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		this.startDate = newStart;
		this.dueDate = newDue;
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

	/**
	 * 대상자 한 명의 납부 상태를 바꾼다.
	 *
	 * @return 실제로 값이 바뀌었으면 {@code true}. 이미 같은 상태였으면 {@code false} 다 —
	 *         일괄 변경이 "몇 명이 바뀌었는지" 세는 데 쓴다.
	 */
	public boolean changePaymentStatus(Member member, PaymentStatus status) {
		requireOpen();
		requireStarted();
		DuesMember target = this.targets.stream()
				.filter(t -> t.getMember().getId().equals(member.getId()))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (target.getStatus() == status) {
			return false;
		}
		target.changeStatus(status);
		return true;
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

	/**
	 * 회비를 마감한다. <b>미납자가 남아 있어도 마감할 수 있다.</b>
	 *
	 * <p>화면명세(DUE-3-MODAL-02-0)의 마감 모달이 "현재 금액이 전체 내역의 수입으로 기록돼요."라고 안내하고,
	 * 마감된 회비 상세에도 미납부 탭이 그대로 남는다. 전원 납부를 요구하면 한 명이라도 끝내 내지 않는 회비를
	 * 영원히 마감할 수 없어, 총무에게 남는 복구 경로가 회비 삭제뿐이 된다.
	 *
	 * <p>마감 시점의 납부 현황이 그대로 굳는다 — 이후 납부 상태는 바꿀 수 없다({@link #requireOpen()}).
	 */
	public void close(Long generatedEntryId) {
		requireOpen();
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
