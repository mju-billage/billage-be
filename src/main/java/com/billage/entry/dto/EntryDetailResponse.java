package com.billage.entry.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.billage.common.response.KoreanTime;
import com.billage.entry.ApprovalStatus;
import com.billage.entry.Entry;
import com.billage.entry.EntryType;
import com.billage.file.dto.ReceiptFileResponse;

/**
 * 내역 상세. 작성자·승인자 이름은 기록 시점 값이라 이후 해당 관리자가 탈퇴해도 그대로 유지된다.
 */
public record EntryDetailResponse(
		Long entryId,
		Long ledgerId,
		String ledgerName,
		EntryType type,
		String title,
		Long amount,
		LocalDate occurredOn,
		String memo,
		ApprovalStatus approvalStatus,
		Actor createdBy,
		/** 담당자. 이 지출·수입의 주체가 되는 관리자이며 작성자와 다를 수 있다. */
		Actor manager,
		Actor approvedBy,
		OffsetDateTime approvedAt,
		List<ReceiptFileResponse> receiptFiles,

		/**
		 * 마감된 회비가 만든 수입 내역이면 채워진다. 일반 내역이면 {@code duesId} 는 null 이고
		 * {@code payers} 는 빈 배열이다.
		 *
		 * <p>회비가 이미 삭제됐어도 {@code duesId}·{@code duesTitle} 은 마감 시점 값으로 남는다 —
		 * 이때 {@code duesExists} 가 false 이고, 화면은 '회비 상세보기' 버튼을 숨긴다.
		 */
		Long duesId,
		String duesTitle,
		boolean duesExists,
		int payerCount,
		List<Payer> payers
) {

	/** 납부자 한 명. 마감 시점에 이 사람이 낸 금액을 함께 보여 준다. */
	public record Payer(Long memberId, String name, Long amount) {
	}

	public record Actor(Long userId, String name) {
	}

	public static EntryDetailResponse of(Entry entry, List<ReceiptFileResponse> receiptFiles) {
		return of(entry, receiptFiles, false, List.of());
	}

	public static EntryDetailResponse of(Entry entry, List<ReceiptFileResponse> receiptFiles,
			boolean duesExists, List<Payer> payers) {
		Actor approvedBy = entry.getApprovedByUserId() == null
				? null
				: new Actor(entry.getApprovedByUserId(), entry.getApprovedByName());
		Actor manager = entry.getManagerUserId() == null
				? null
				: new Actor(entry.getManagerUserId(), entry.getManagerName());

		return new EntryDetailResponse(entry.getId(), entry.getLedger().getId(), entry.getLedger().getName(),
				entry.getType(), entry.getTitle(), entry.getAmount(), entry.getOccurredOn(), entry.getMemo(),
				entry.getApprovalStatus(), new Actor(entry.getCreatedByUserId(), entry.getCreatedByName()),
				manager, approvedBy, KoreanTime.toOffset(entry.getApprovedAt()), receiptFiles,
				entry.getDuesId(), entry.getDuesTitle(), duesExists, payers.size(), payers);
	}
}
