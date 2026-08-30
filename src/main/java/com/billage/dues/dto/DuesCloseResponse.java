package com.billage.dues.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.dues.Dues;
import com.billage.dues.DuesStatus;

/** 회비 마감 응답. {@code generatedEntryId} 는 이 마감으로 장부에 만들어진 수입 내역이다. */
public record DuesCloseResponse(
		Long duesId,
		DuesStatus status,
		long paidCount,
		long targetCount,
		long totalCollectedAmount,
		Long ledgerId,
		Long generatedEntryId,
		OffsetDateTime closedAt
) {

	public static DuesCloseResponse from(Dues dues) {
		return new DuesCloseResponse(dues.getId(), dues.phase(), dues.paidCount(), dues.targetCount(),
				dues.totalCollectedAmount(), dues.getLedgerId(), dues.getGeneratedEntryId(),
				KoreanTime.toOffset(dues.getClosedAt()));
	}
}
