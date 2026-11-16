package com.billage.entry.dto;

import java.time.LocalDate;

import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;
import com.billage.entry.EntryType;

/**
 * 모임 전체 내역 목록 항목.
 *
 * <p>장부별 목록({@link EntrySummaryResponse})과 달리 여러 장부가 섞여 나오므로
 * 어느 장부의 내역인지 함께 내려 준다 — 화면도 내역명 왼쪽에 장부 태그를 붙인다.
 *
 * @param duesId 마감된 회비가 만든 수입 내역이면 그 회비의 ID, 일반 내역이면 null.
 *               회비가 이미 삭제됐어도 마감 시점 값으로 남는다({@link EntryDetailResponse} 와 같다).
 */
public record GroupEntrySummaryResponse(
		Long entryId,
		Long ledgerId,
		String ledgerName,
		EntryType type,
		String title,
		Long amount,
		LocalDate occurredOn,
		ApprovalStatus approvalStatus,
		Long createdByUserId,
		String createdByName,
		long receiptCount,
		Long duesId
) {

	public static GroupEntrySummaryResponse of(Entry entry, long receiptCount) {
		return new GroupEntrySummaryResponse(entry.getId(), entry.getLedger().getId(), entry.getLedger().getName(),
				entry.getType(), entry.getTitle(), entry.getAmount(), entry.getOccurredOn(),
				entry.getApprovalStatus(), entry.getCreatedByUserId(), entry.getCreatedByName(), receiptCount,
				entry.getDuesId());
	}
}
