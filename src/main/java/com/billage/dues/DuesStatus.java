package com.billage.dues;

import java.time.LocalDate;

/**
 * 회비 진행 상태. 마감 취소(재오픈)는 화면명세에 없어 {@link #CLOSED} 는 종착 상태다.
 *
 * <p><b>DB 에 저장하는 값은 {@link #OPEN} 과 {@link #CLOSED} 뿐이다.</b> {@link #SCHEDULED} 는
 * 시작일과 오늘을 비교해 읽는 시점에 파생시키는 값이며({@link Dues#phase()}) 컬럼에 들어가지 않는다.
 * 별도 상태로 저장하면 시작일이 되는 순간 OPEN 으로 바꿔 줄 배치가 필요해지고, 그 배치가 밀리면
 * 화면과 DB 가 어긋난다.
 */
public enum DuesStatus {

	/** 납부 예정 — 시작일 전. 대상자와 기본 정보는 고칠 수 있지만 납부 상태는 바꿀 수 없다. */
	SCHEDULED,

	/** 납부 진행 중 — 시작일 이후, 마감 전. */
	OPEN,

	/** 마감 — 납부 상태를 더 이상 바꿀 수 없고 요약 수치가 마감 시점 값으로 굳는다. */
	CLOSED;

	/** 저장된 상태와 시작일로 화면에 보여 줄 상태를 정한다. */
	static DuesStatus phaseOf(DuesStatus persisted, LocalDate startDate, LocalDate today) {
		if (persisted == CLOSED) {
			return CLOSED;
		}
		return today.isBefore(startDate) ? SCHEDULED : OPEN;
	}
}
