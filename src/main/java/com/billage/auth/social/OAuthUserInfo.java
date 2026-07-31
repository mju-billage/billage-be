package com.billage.auth.social;

/**
 * Provider 토큰 검증 결과로 얻은 사용자 식별 정보.
 *
 * @param providerUserId Provider 내 고유 사용자 식별자 (구글 sub, 카카오 id)
 * @param email          Provider가 검증한 이메일
 */
public record OAuthUserInfo(String providerUserId, String email) {
}
