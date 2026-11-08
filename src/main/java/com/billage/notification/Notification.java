package com.billage.notification;

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
 * 받는 사람 한 명의 알림 한 건. 문구는 만들 때 확정해 저장한다 — 나중에 내역 제목이 바뀌어도 알림은 그대로다.
 * 회원 탈퇴·모임 삭제 시 DB FK cascade 로 함께 지워진다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

	public static final int MAX_TITLE_LENGTH = 100;
	public static final int MAX_BODY_LENGTH = 255;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "group_id", updatable = false)
	private Long groupId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private NotificationCategory category;

	@Column(nullable = false, length = MAX_TITLE_LENGTH, updatable = false)
	private String title;

	@Column(nullable = false, length = MAX_BODY_LENGTH, updatable = false)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", length = 20, updatable = false)
	private NotificationTargetType targetType;

	@Column(name = "target_id", updatable = false)
	private Long targetId;

	@Column(name = "read_at")
	private LocalDateTime readAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private Notification(Long userId, Long groupId, NotificationCategory category, String title, String body,
			NotificationTargetType targetType, Long targetId) {
		this.userId = userId;
		this.groupId = groupId;
		this.category = category;
		this.title = truncate(title, MAX_TITLE_LENGTH);
		this.body = truncate(body, MAX_BODY_LENGTH);
		this.targetType = targetType;
		this.targetId = targetId;
	}

	static Notification create(Long userId, NotificationMessage message) {
		return new Notification(userId, message.groupId(), message.category(), message.title(), message.body(),
				message.targetType(), message.targetId());
	}

	/** 이미 읽었으면 처음 읽은 시각을 그대로 둔다. */
	void markRead(LocalDateTime at) {
		if (this.readAt == null) {
			this.readAt = at;
		}
	}

	/** 내역명·모임명이 길어도 저장이 실패하지 않게 자른다(알림 실패로 본 작업이 막히면 안 된다). */
	private static String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
