package com.billage.membership;

import java.time.LocalDateTime;

import com.billage.group.GroupSpace;

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
 * 가입 사용자(User)와 모임의 **관리자 관계**. 총무/일반 권한을 여기에 저장한다.
 * 납부 관리에 쓰는 모임원 명단({@code com.billage.member.Member})과는 별개이며 자동 연결하지 않는다.
 */
@Entity
@Table(name = "group_membership")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMembership {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private GroupSpace group;

	/** 인증 도메인과 결합하지 않도록 식별자만 보관한다. */
	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GroupRole role;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Version
	private Long version;

	private GroupMembership(GroupSpace group, Long userId, GroupRole role) {
		this.group = group;
		this.userId = userId;
		this.role = role;
		this.joinedAt = LocalDateTime.now();
	}

	/** 모임 생성자. 최초 총무로 등록된다. */
	public static GroupMembership createOwner(GroupSpace group, Long userId) {
		return new GroupMembership(group, userId, GroupRole.OWNER);
	}

	/** 초대 코드로 참여한 사용자. 납부 명단은 만들지 않는다. */
	public static GroupMembership join(GroupSpace group, Long userId) {
		return new GroupMembership(group, userId, GroupRole.MEMBER);
	}

	public boolean isOwner() {
		return this.role == GroupRole.OWNER;
	}

	public void changeRole(GroupRole role) {
		this.role = role;
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
