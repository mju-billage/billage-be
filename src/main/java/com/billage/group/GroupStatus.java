package com.billage.group;

/**
 * 모임 상태. 모임은 즉시 물리 삭제하지 않고 {@code ARCHIVED}로 보관한다(보관 기능은 후속 단계).
 */
public enum GroupStatus {
	ACTIVE,
	ARCHIVED
}
