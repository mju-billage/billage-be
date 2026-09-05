package com.billage.user.dto;

import java.util.List;

import com.billage.user.WithdrawalReasonType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 회원 탈퇴(화면 COM-1-PAGE-02-0 → COM-2-PAGE-04-0 → COM-2-PAGE-05-0).
 * 권한 이전과 계정 삭제는 마지막 확인에서 한 번에 처리되므로 요청도 하나다.
 *
 * @param ownershipTransfers 내가 유일한 총무인 모임마다 넘겨받을 관리자를 하나씩 지정한다.
 *                           나 말고 관리자가 없는 모임은 넘길 곳이 없어 모임째 삭제되며, 여기 넣지 않는다.
 * @param reasons            1개 이상 다중 선택.
 * @param reasonDetail       {@code ETC} 를 골랐을 때 필수, 최대 30자.
 */
public record WithdrawRequest(
		@Valid List<OwnershipTransfer> ownershipTransfers,

		@NotEmpty(message = "탈퇴 사유를 하나 이상 선택해 주세요.")
		List<WithdrawalReasonType> reasons,

		@Size(max = 30, message = "탈퇴 사유는 30자 이하여야 합니다.")
		String reasonDetail
) {

	public List<OwnershipTransfer> transfers() {
		return ownershipTransfers == null ? List.of() : ownershipTransfers;
	}

	public record OwnershipTransfer(
			@NotNull Long groupId,
			@NotNull Long newOwnerUserId
	) {
	}
}
