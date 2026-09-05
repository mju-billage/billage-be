package com.billage.auth.social;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

	Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	/** 내 프로필의 로그인 수단 표시에 쓴다. 한 계정에 구글·카카오가 함께 연결될 수 있다. */
	List<SocialAccount> findByUserId(Long userId);

	/** 탈퇴 시 소셜 연결을 끊는다. 계정과 함께 사라지는 데이터다. */
	void deleteByUserId(Long userId);
}
