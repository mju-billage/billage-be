package com.billage.dues.dto;

import com.billage.dues.Dues;
import com.billage.dues.PaymentStatus;

/**
 * 납부 상태 일괄 변경 결과.
 *
 * <p>{@code changedCount} 는 실제로 상태가 바뀐 인원이다 — 이미 요청한 상태였던 사람은 세지 않는다.
 * 화면 스낵바("{N}명의 납부가 확인되었어요.")가 이 값을 쓴다.
 */
public record PaymentStatusBulkUpdateResponse(
		Long duesId,
		int changedCount,
		PaymentStatus status,
		long paidCount,
		long unpaidCount,
		long targetCount,
		long totalCollectedAmount
) {

	public static PaymentStatusBulkUpdateResponse of(Dues dues, int changedCount, PaymentStatus status) {
		long targetCount = dues.targetCount();
		long paidCount = dues.paidCount();
		return new PaymentStatusBulkUpdateResponse(dues.getId(), changedCount, status,
				paidCount, targetCount - paidCount, targetCount, dues.totalCollectedAmount());
	}
}
