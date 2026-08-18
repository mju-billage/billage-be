package com.billage.membership;

import java.time.LocalDateTime;

import com.billage.group.GroupSpace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모임 관리자 참여용 초대 코드. 총무가 발급하며 만료 시각까지 여러 명이 사용할 수 있다(1회용 아님).
 * 이 코드로 참여해도 납부 명단(Member)은 생성되지 않는다.
 */
@Entity
@Table(name = "group_invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupInvitation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private GroupSpace group;

	@Column(nullable = false, length = 20, updatable = false)
	private String code;

	@Column(name = "created_by", nullable = false, updatable = false)
	private Long createdBy;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private GroupInvitation(GroupSpace group, String code, Long createdBy, LocalDateTime expiresAt) {
		this.group = group;
		this.code = code;
		this.createdBy = createdBy;
		this.expiresAt = expiresAt;
	}

	public static GroupInvitation issue(GroupSpace group, String code, Long createdBy, LocalDateTime expiresAt) {
		return new GroupInvitation(group, code, createdBy, expiresAt);
	}

	public boolean isExpired(LocalDateTime at) {
		return this.expiresAt.isBefore(at);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
