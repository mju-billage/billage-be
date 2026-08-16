package com.billage.member.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.member.Member;

/** 납부 관리·모임원 명단 항목. 권한 정보를 담지 않는다. */
public record MemberResponse(
		Long memberId,
		String name,
		OffsetDateTime createdAt
) {

	public static MemberResponse from(Member member) {
		return new MemberResponse(member.getId(), member.getName(), KoreanTime.toOffset(member.getCreatedAt()));
	}
}
