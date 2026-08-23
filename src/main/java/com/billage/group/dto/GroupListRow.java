package com.billage.group.dto;

import java.time.LocalDateTime;

import com.billage.membership.GroupRole;

/**
 * 내 모임 목록 조회 쿼리 결과(JPQL 생성자 표현식용). 응답 변환은 Service 에서 한다.
 */
public record GroupListRow(
		Long groupId,
		String name,
		String description,
		Long groupImageFileId,
		GroupRole myRole,
		long memberCount,
		LocalDateTime createdAt
) {
}
