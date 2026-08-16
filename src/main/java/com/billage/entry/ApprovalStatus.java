package com.billage.entry;

/**
 * 승인 상태. 반려(REJECTED)는 MVP 범위에서 제외한다 —
 * 내용이 부족한 승인 대기 내역은 총무가 수정한 뒤 승인한다.
 */
public enum ApprovalStatus {
	PENDING,
	APPROVED
}
