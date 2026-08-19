package com.billage.dues.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.dues.Dues;
import com.billage.dues.DuesMember;
import com.billage.dues.PaymentStatus;

/** 납부 상태 변경 응답. 변경 후 집계를 함께 준다. */
public record PaymentStatusUpdateResponse(
		Long duesId,
		Long memberId,
		String name,
		PaymentStatus status,
		OffsetDateTime paidAt,
		long paidCount,
		long targetCount
) {

	public static PaymentStatusUpdateResponse of(Dues dues, DuesMember target) {
		return new PaymentStatusUpdateResponse(dues.getId(), target.getMember().getId(),
				target.getMember().getName(), target.getStatus(), KoreanTime.toOffset(target.getPaidAt()),
				dues.paidCount(), dues.targetCount());
	}
}
