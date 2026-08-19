package com.billage.dues.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.dues.Dues;
import com.billage.dues.DuesStatus;

/**
 * 회비 상세. {@code expectedTotalAmount} 는 대상 전원이 냈을 때의 금액이고,
 * 실제로 걷힌 금액은 납부 완료 인원 × 1인당 금액이다.
 */
public record DuesDetailResponse(
		Long duesId,
		Long groupId,
		String title,
		Long amount,
		LocalDate dueDate,
		DuesStatus status,
		long paidCount,
		long unpaidCount,
		long targetCount,
		long expectedTotalAmount,
		LedgerRef ledger,
		OffsetDateTime createdAt,
		OffsetDateTime closedAt,
		Long generatedEntryId
) {

	/** 장부가 삭제됐으면 name 이 null 이다. */
	public record LedgerRef(Long ledgerId, String name) {
	}

	public static DuesDetailResponse of(Dues dues, String ledgerName) {
		long targetCount = dues.targetCount();
		long paidCount = dues.paidCount();

		return new DuesDetailResponse(dues.getId(), dues.getGroupId(), dues.getTitle(), dues.getAmount(),
				dues.getDueDate(), dues.getStatus(), paidCount, targetCount - paidCount, targetCount,
				targetCount * dues.getAmount(),
				new LedgerRef(dues.getLedgerId(), ledgerName),
				KoreanTime.toOffset(dues.getCreatedAt()), KoreanTime.toOffset(dues.getClosedAt()),
				dues.getGeneratedEntryId());
	}
}
