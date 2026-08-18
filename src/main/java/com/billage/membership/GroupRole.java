package com.billage.membership;

/**
 * 모임 관리자 권한. 납부 명단({@code com.billage.member.Member})에는 저장하지 않는다.
 * <ul>
 *   <li>{@code OWNER} — 총무. 모임 설정·명단·폴더·장부 관리 권한. 공동 총무 수 제한 없음, 항상 최소 1명 필요.</li>
 *   <li>{@code MEMBER} — 일반 권한 관리자.</li>
 * </ul>
 */
public enum GroupRole {
	OWNER,
	MEMBER
}
