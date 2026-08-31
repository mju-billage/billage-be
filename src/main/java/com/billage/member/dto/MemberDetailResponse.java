package com.billage.member.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.billage.common.response.KoreanTime;
import com.billage.member.Member;

/**
 * 모임원 상세. 목록에 없는 <b>총 납부 금액</b>을 함께 낸다 —
 * 화면 상단 카드가 이 값을 보여 주고, 카드를 누르면 납부 내역으로 들어간다.
 */
public record MemberDetailResponse(
		Long memberId,
		String name,
		String phoneNumber,
		List<String> tags,
		String memo,
		/** 이 모임원이 납부 완료로 기록된 회비 금액의 합. 저장하지 않고 매번 계산한다. */
		long totalPaidAmount,
		OffsetDateTime createdAt
) {

	public static MemberDetailResponse of(Member member, long totalPaidAmount) {
		return new MemberDetailResponse(member.getId(), member.getName(), member.getPhoneNumber(),
				member.sortedTags(), member.getMemo(), totalPaidAmount,
				KoreanTime.toOffset(member.getCreatedAt()));
	}
}
