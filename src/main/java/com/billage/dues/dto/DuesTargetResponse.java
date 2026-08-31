package com.billage.dues.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.dues.Dues;
import com.billage.dues.DuesMember;
import com.billage.dues.PaymentStatus;

/**
 * 납부 대상 한 명.
 *
 * <p>{@code amount} 는 이 사람에게 할당된 금액이다. 미납이면 아직 걷힌 게 없으므로 {@code 0} 이며,
 * 화면도 미납부 탭에서는 금액을 숨기고 납부 완료 탭에서만 노출한다.
 */
public record DuesTargetResponse(
		Long memberId,
		String name,
		PaymentStatus status,
		Long amount,
		OffsetDateTime paidAt
) {

	public static DuesTargetResponse of(Dues dues, DuesMember target) {
		long amount = target.isPaid() ? dues.getAmount() : 0L;
		return new DuesTargetResponse(target.getMember().getId(), target.getMember().getName(),
				target.getStatus(), amount, KoreanTime.toOffset(target.getPaidAt()));
	}
}
