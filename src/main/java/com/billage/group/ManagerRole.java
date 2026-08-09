package com.billage.group;

/**
 * 모임 관리 권한. 권한 주체는 {@link GroupManager}이며 모임원 명단(GroupMember)과는 무관하다.
 * <ul>
 *   <li>{@code OWNER}: 모임을 생성한 총무. 모든 관리 기능 사용 가능.</li>
 *   <li>{@code GENERAL}: 초대 코드로 참여한 일반 관리자. 조회·내역 등록(승인 대기)만 가능.</li>
 * </ul>
 */
public enum ManagerRole {
	OWNER,
	GENERAL
}
