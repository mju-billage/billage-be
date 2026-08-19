package com.billage.dues;

import java.time.LocalDateTime;

import com.billage.member.Member;

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
 * 회비의 납부 대상 한 명. 대상은 가입 사용자가 아니라 납부 명단({@link Member}) 기준이다.
 *
 * <p>납부 완료 전환은 실제 입금과 연동되지 않는 <b>총무의 수기 조작</b>이다.
 * 그래서 되돌리는 것(PAID → UNPAID)도 막지 않는다.
 */
@Entity
@Table(name = "dues_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DuesMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dues_id", nullable = false, updatable = false)
	private Dues dues;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false, updatable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	private DuesMember(Dues dues, Member member) {
		this.dues = dues;
		this.member = member;
		this.status = PaymentStatus.UNPAID;
	}

	static DuesMember of(Dues dues, Member member) {
		return new DuesMember(dues, member);
	}

	public boolean isPaid() {
		return this.status == PaymentStatus.PAID;
	}

	/** 납부 상태 변경. UNPAID 로 되돌리면 납부 시각도 지운다. */
	void changeStatus(PaymentStatus status) {
		this.status = status;
		this.paidAt = status == PaymentStatus.PAID ? LocalDateTime.now() : null;
	}
}
