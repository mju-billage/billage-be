package com.billage.notification;

/** 알림 카테고리. 화면이 카테고리별로 아이콘을 다르게 붙이고, 설정 토글도 이 단위로 끈다. */
public enum NotificationCategory {
	GROUP_ACTIVITY,
	APPROVAL,
	DUES,
	/** 공지 및 업데이트. 공지는 마이그레이션으로 넣으므로 지금은 만드는 이벤트가 없다. */
	NOTICE,
	/** 마케팅. 지금은 만드는 이벤트가 없고 수신 동의만 저장한다. */
	MARKETING
}
