package com.billage.user.dto;

import java.time.OffsetDateTime;

import com.billage.common.response.KoreanTime;
import com.billage.user.User;

/**
 * 내 프로필(화면 ETC-3-PAGE-07-0). 수정 응답도 같은 형식을 쓴다.
 *
 * @param loginProvider {@code EMAIL | GOOGLE | KAKAO}. 화면이 계정 정보 카드에 아이콘을 그리고,
 *                      {@code EMAIL} 이 아니면 「비밀번호 변경」 메뉴를 숨긴다.
 * @param createdAt     가입일. 화면에 {@code YYYY.MM.DD} 로 노출된다.
 */
public record MyProfileResponse(
		Long userId,
		String email,
		String name,
		String profileImageUrl,
		String loginProvider,
		OffsetDateTime createdAt
) {

	public static MyProfileResponse of(User user, String profileImageUrl, String loginProvider) {
		return new MyProfileResponse(user.getId(), user.getEmail(), user.getName(), profileImageUrl,
				loginProvider, KoreanTime.toOffset(user.getCreatedAt()));
	}
}
