package com.billage.group;

import java.time.LocalDateTime;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자의 모임 관리 권한(권한 주체). {@code (groupId, userId)}가 유일하다.
 * <p>
 * 인증 도메인과의 결합을 피하려고 {@code User} 엔티티를 참조하지 않고 {@code userId}만 보관한다.
 * 모임원 명단(GroupMember)과는 별개이며, 모든 모임 리소스 권한 검증은 이 엔티티의 역할로 판정한다.
 */
@Entity
@Table(name = "group_managers",
		uniqueConstraints = @UniqueConstraint(name = "uk_group_managers_group_user",
				columnNames = {"group_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupManager {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private Group group;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ManagerRole role;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private GroupManager(Group group, Long userId, ManagerRole role) {
		this.group = group;
		this.userId = userId;
		this.role = role;
	}

	/** 모임 생성자를 OWNER로 등록한다. */
	public static GroupManager owner(Group group, Long userId) {
		return new GroupManager(group, userId, ManagerRole.OWNER);
	}

	/** 초대 코드로 참여한 사용자를 GENERAL로 등록한다(step 3에서 사용). */
	public static GroupManager general(Group group, Long userId) {
		return new GroupManager(group, userId, ManagerRole.GENERAL);
	}

	public boolean isOwner() {
		return role == ManagerRole.OWNER;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
