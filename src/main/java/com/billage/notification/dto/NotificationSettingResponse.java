package com.billage.notification.dto;

import com.billage.notification.NotificationSetting;

/** 알림 설정 토글 6종. {@code marketing} 은 가입 때 받은 마케팅 수신 동의와 같은 값이다. */
public record NotificationSettingResponse(
		boolean groupActivity,
		boolean approval,
		boolean dues,
		boolean noticeAndUpdate,
		boolean marketing,
		boolean nightTime
) {

	public static NotificationSettingResponse of(NotificationSetting setting, boolean marketing) {
		return new NotificationSettingResponse(setting.isGroupActivity(), setting.isApproval(), setting.isDues(),
				setting.isNoticeAndUpdate(), marketing, setting.isNightTime());
	}
}
