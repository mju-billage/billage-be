package com.billage.notification.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.notification.Notification;
import com.billage.notification.NotificationCategory;
import com.billage.notification.NotificationTargetType;

/** 알림 한 건. 경과 시간 표기("방금 전")는 클라이언트가 {@code createdAt} 으로 만든다. */
public record NotificationResponse(
		Long notificationId,
		NotificationCategory category,
		String title,
		String body,
		Long groupId,
		NotificationTargetType targetType,
		Long targetId,
		OffsetDateTime readAt,
		OffsetDateTime createdAt
) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(notification.getId(), notification.getCategory(), notification.getTitle(),
				notification.getBody(), notification.getGroupId(), notification.getTargetType(),
				notification.getTargetId(),
				notification.getReadAt() == null ? null : KoreanTime.toOffset(notification.getReadAt()),
				KoreanTime.toOffset(notification.getCreatedAt()));
	}
}
