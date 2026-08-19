package com.billage.dues;

/** 회비 진행 상태. 마감 취소(재오픈)는 명세에 없어 CLOSED 는 종착 상태다. */
public enum DuesStatus {
	OPEN,
	CLOSED
}
