package com.billage.group;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하나의 동아리/모임. 테이블명 {@code groups}는 MySQL 예약어라 인용부호로 감싼다.
 * <p>
 * 모임 관리 권한은 {@link GroupManager}로, 모임원 명단은 GroupMember로 분리해 관리한다.
 * 초대 코드는 재발급 가능한 단일 코드로, 만료 개념은 두지 않는다(런칭 최소 범위).
 */
@Entity
@Table(name = "`groups`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(name = "invite_code", nullable = false, unique = true, length = 16)
	private String inviteCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GroupStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private Group(String name, String inviteCode) {
		this.name = name;
		this.inviteCode = inviteCode;
		this.status = GroupStatus.ACTIVE;
	}

	/** 새 모임 생성. 상태는 ACTIVE로 시작한다. 초대 코드는 호출 측에서 유일성을 보장해 전달한다. */
	public static Group create(String name, String inviteCode) {
		return new Group(name, inviteCode);
	}

	/** 초대 코드 재발급. 이전 코드는 즉시 무효가 된다. */
	public void changeInviteCode(String inviteCode) {
		this.inviteCode = inviteCode;
	}

	/** 보관(ARCHIVED)된 모임에는 새로 참여할 수 없다. */
	public boolean isActive() {
		return status == GroupStatus.ACTIVE;
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
