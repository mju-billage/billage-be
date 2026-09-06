package com.billage.user;

/** 탈퇴 사유(화면 COM-2-PAGE-05-0의 선택지). 다중 선택이며 {@link #ETC} 는 직접 입력을 함께 받는다. */
public enum WithdrawalReasonType {
	/** 사용법을 모르겠어요 */
	USAGE_UNCLEAR,
	/** 다시 가입할 거예요 */
	REJOIN,
	/** 원하는 기능이 없어요 */
	MISSING_FEATURE,
	/** 더 이상 필요하지 않아요 */
	NO_LONGER_NEEDED,
	/** 기타(직접 입력) */
	ETC
}
