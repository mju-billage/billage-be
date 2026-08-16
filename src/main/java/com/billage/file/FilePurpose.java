package com.billage.file;

/** 파일 용도. 용도별로 연결 대상과 삭제 조건이 다르다. */
public enum FilePurpose {
	/** 증빙·영수증. 내역(Entry)에 연결된다. */
	RECEIPT,
	/** 사용자 프로필 이미지. 연결 로직은 User 수정 API 구현 시 추가. */
	PROFILE_IMAGE,
	/** 모임 이미지. 연결 로직은 모임 이미지 필드 적용 시 추가. */
	GROUP_IMAGE
}
