package com.billage.dues.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.dues.DuesMember;
import com.billage.dues.PaymentStatus;

/** 납부 대상 목록 항목. 부분·초과 납부액 필드는 제공하지 않는다. */
public record DuesTargetResponse(
		Long memberId,
		String name,
		PaymentStatus status,
		OffsetDateTime paidAt
) {

	public static DuesTargetResponse from(DuesMember target) {
		return new DuesTargetResponse(target.getMember().getId(), target.getMember().getName(),
				target.getStatus(), KoreanTime.toOffset(target.getPaidAt()));
	}
}
