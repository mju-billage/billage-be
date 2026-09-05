package com.billage.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 탈퇴 사유. 통계 목적이라 <b>누가 냈는지는 남기지 않는다</b>(명세: "개인 식별 정보와 분리해 저장").
 * 사용자를 가리키는 컬럼이 없는 것은 빠뜨린 것이 아니라 의도한 설계다.
 */
@Entity
@Table(name = "withdrawal_reason")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawalReason {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private WithdrawalReasonType reason;

	/** 기타를 골랐을 때 직접 입력한 내용. */
	@Column(length = 30)
	private String detail;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private WithdrawalReason(WithdrawalReasonType reason, String detail) {
		this.reason = reason;
		this.detail = detail;
	}

	public static WithdrawalReason of(WithdrawalReasonType reason, String detail) {
		return new WithdrawalReason(reason, detail);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
