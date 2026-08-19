package com.billage.dues;

/** 대상자별 납부 상태. 부분·초과 납부는 지원하지 않는다(기획 확정). */
public enum PaymentStatus {
	UNPAID,
	PAID
}
