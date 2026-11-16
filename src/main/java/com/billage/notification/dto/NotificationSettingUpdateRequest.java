package com.billage.notification.dto;

/** 알림 설정 부분 수정. 보낸 값만 바꾸고 null 은 그대로 둔다(토글 하나씩 즉시 저장하는 화면). */
public record NotificationSettingUpdateRequest(
		Boolean groupActivity,
		Boolean approval,
		Boolean dues,
		Boolean noticeAndUpdate,
		/** 가입 때 받은 마케팅 수신 동의를 함께 바꾼다. */
		Boolean marketing,
		Boolean nightTime
) {
}
