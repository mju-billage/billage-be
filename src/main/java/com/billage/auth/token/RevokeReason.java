package com.billage.auth.token;

/**
 * Refresh Token 폐기 사유.
 * <ul>
 *     <li>{@link #ROTATED} — 재발급(Rotation)으로 정상 폐기</li>
 *     <li>{@link #LOGOUT} — 로그아웃으로 폐기</li>
 *     <li>{@link #REUSED} — 이미 회전된 토큰의 재사용 감지로 패밀리 전체 폐기</li>
 * </ul>
 */
public enum RevokeReason {
	ROTATED,
	LOGOUT,
	REUSED
}
