package com.billage.notification;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자의 알림 수신 설정. 행이 없으면 {@link #defaults(Long)} 와 같다 — 가입 때 미리 만들지 않고 처음 바꿀 때 만든다.
 * 마케팅 수신은 여기 두지 않는다(users.marketing_agreed_at 하나로 관리). {@code nightTime} 은 푸시 전용 의미라
 * 앱 안 알림 생성에는 영향이 없고 저장만 한다.
 */
@Entity
@Table(name = "notification_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "group_activity", nullable = false)
	private boolean groupActivity;

	@Column(nullable = false)
	private boolean approval;

	@Column(nullable = false)
	private boolean dues;

	@Column(name = "notice_and_update", nullable = false)
	private boolean noticeAndUpdate;

	@Column(name = "night_time", nullable = false)
	private boolean nightTime;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private NotificationSetting(Long userId) {
		this.userId = userId;
		this.groupActivity = true;
		this.approval = true;
		this.dues = true;
		this.noticeAndUpdate = true;
		// 야간(21시~08시) 수신은 별도 동의가 필요해 기본값을 끈다.
		this.nightTime = false;
	}

	/** 아직 한 번도 바꾸지 않은 사용자의 설정. */
	public static NotificationSetting defaults(Long userId) {
		return new NotificationSetting(userId);
	}

	/** 전달된 값만 바꾼다(null 은 그대로). */
	void update(Boolean groupActivity, Boolean approval, Boolean dues, Boolean noticeAndUpdate, Boolean nightTime) {
		if (groupActivity != null) {
			this.groupActivity = groupActivity;
		}
		if (approval != null) {
			this.approval = approval;
		}
		if (dues != null) {
			this.dues = dues;
		}
		if (noticeAndUpdate != null) {
			this.noticeAndUpdate = noticeAndUpdate;
		}
		if (nightTime != null) {
			this.nightTime = nightTime;
		}
	}

	/** 이 카테고리의 알림을 받는지. 마케팅은 여기서 판단하지 않는다(사용자 동의값). */
	boolean receives(NotificationCategory category) {
		return switch (category) {
			case GROUP_ACTIVITY -> groupActivity;
			case APPROVAL -> approval;
			case DUES -> dues;
			case NOTICE -> noticeAndUpdate;
			case MARKETING -> throw new IllegalArgumentException("마케팅 수신은 사용자 동의값으로 판단한다.");
		};
	}

	@PrePersist
	@PreUpdate
	void touch() {
		this.updatedAt = LocalDateTime.now();
	}
}
