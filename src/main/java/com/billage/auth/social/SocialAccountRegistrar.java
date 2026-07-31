package com.billage.auth.social;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.billage.user.User;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 계정 생성·소셜 계정 연결을 별도 트랜잭션(REQUIRES_NEW)으로 격리한다.
 * 같은 트랜잭션에서 UNIQUE 제약 위반을 잡아 복구를 시도하면 Hibernate가 세션을
 * rollback-only로 표시해 이후 커밋이 실패할 수 있다 — 충돌 시 이 트랜잭션만 롤백되고
 * 호출자({@link SocialAuthService})의 트랜잭션은 영향받지 않는다.
 */
@Component
@RequiredArgsConstructor
class SocialAccountRegistrar {

	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public User register(SocialProvider provider, OAuthUserInfo info, String name) {
		LocalDateTime now = LocalDateTime.now();
		User user = userRepository.findByEmail(info.email())
				.orElseGet(() -> userRepository.save(User.createSocial(info.email(), name, now)));
		user.agreeToTermsIfNeeded(now);
		socialAccountRepository.save(SocialAccount.link(user, provider, info.providerUserId(), info.email()));
		return user;
	}
}
