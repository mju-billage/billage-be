package com.billage.notification;

/** 여러 사람에게 같은 내용으로 보낼 알림 한 건의 내용. */
public record NotificationMessage(
		Long groupId,
		NotificationCategory category,
		String title,
		String body,
		NotificationTargetType targetType,
		Long targetId
) {
}
