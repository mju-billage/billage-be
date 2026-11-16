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

	/**
	 * @param marketing 마케팅 수신 동의. 항목별 동의 없이 온 예전 요청이면 null 이다.
	 *                  새로 만드는 계정에만 적는다 — 이미 있는 계정에 소셜을 연결하는 경우,
	 *                  그 계정이 가입 때 남긴 동의 기록을 덮어쓰지 않는다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public User register(SocialProvider provider, OAuthUserInfo info, String name, Boolean marketing) {
		LocalDateTime now = LocalDateTime.now();
		User user = userRepository.findByEmail(info.email())
				.orElseGet(() -> {
					User created = User.createSocial(info.email(), name, now);
					if (marketing != null) {
						created.recordMarketingAgreement(marketing, now);
					}
					return userRepository.save(created);
				});
		user.agreeToTermsIfNeeded(now);
		socialAccountRepository.save(SocialAccount.link(user, provider, info.providerUserId(), info.email()));
		return user;
	}
}
