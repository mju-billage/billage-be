package com.billage.group;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하나의 동아리/모임. {@code Group} 은 Java·SQL 예약어와 충돌하므로 엔티티명은 GroupSpace,
 * API 경로는 {@code /groups} 를 사용한다.
 */
@Entity
@Table(name = "group_space")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupSpace {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(length = 255)
	private String description;

	/** 모임을 만든 사용자. 인증 도메인과 결합하지 않도록 식별자만 보관한다. */
	@Column(name = "created_by", nullable = false, updatable = false)
	private Long createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Version
	private Long version;

	private GroupSpace(String name, String description, Long createdBy) {
		this.name = name;
		this.description = description;
		this.createdBy = createdBy;
	}

	public static GroupSpace create(String name, String description, Long createdBy) {
		return new GroupSpace(name, description, createdBy);
	}

	/** PATCH 부분 수정. null 로 전달된 필드는 변경하지 않는다. */
	public void update(String name, String description) {
		if (name != null) {
			this.name = name;
		}
		if (description != null) {
			this.description = description;
		}
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
