package com.billage.auth.social;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.auth.AuthService;
import com.billage.auth.dto.LoginResponse;
import com.billage.auth.dto.SocialLoginResponse;
import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.user.User;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 간편 로그인(구글·카카오) 흐름:
 * 1) {@link #login}: 연결된 계정이 있으면 즉시 로그인, 없으면 최초 로그인으로 판단해 이메일만 반환.
 * 2) {@link #signup}: 클라이언트가 약관 동의·이름 입력을 받은 뒤 호출 — 계정을 생성하고 로그인 처리한다.
 */
@Service
@RequiredArgsConstructor
public class SocialAuthService {

	private final List<SocialTokenVerifier> verifiers;
	private final SocialAccountRepository socialAccountRepository;
	private final UserRepository userRepository;
	private final AuthService authService;

	@Transactional
	public SocialLoginResponse login(SocialProvider provider, String token) {
		OAuthUserInfo info = verify(provider, token);
		return socialAccountRepository.findByProviderAndProviderUserId(provider, info.providerUserId())
				.map(account -> SocialLoginResponse.loggedIn(authService.issueLoginTokens(account.getUser())))
				.orElseGet(() -> SocialLoginResponse.signupRequired(info.email()));
	}

	/**
	 * 이미 연결된 계정이 재요청하면(중복 클릭 등) 새로 생성하지 않고 로그인으로 처리한다.
	 * 같은 이메일의 계정이 다른 Provider로 이미 있으면 새 계정을 만들지 않고 거기에 연결한다
	 * (Provider가 이메일 소유를 검증했으므로 안전).
	 */
	@Transactional
	public LoginResponse signup(SocialProvider provider, String token, String name) {
		OAuthUserInfo info = verify(provider, token);

		return socialAccountRepository.findByProviderAndProviderUserId(provider, info.providerUserId())
				.map(account -> authService.issueLoginTokens(account.getUser()))
				.orElseGet(() -> createAccountAndLogin(provider, info, name));
	}

	/**
	 * 동시에 들어온 중복 요청이 먼저 계정을 만들면 UNIQUE 제약(social_accounts, users.email)에 걸린다.
	 * 이때 새로 만들지 않고 그 계정으로 로그인 처리해 idempotent하게 응답한다.
	 */
	private LoginResponse createAccountAndLogin(SocialProvider provider, OAuthUserInfo info, String name) {
		try {
			User user = userRepository.findByEmail(info.email())
					.orElseGet(() -> userRepository.save(User.createSocial(info.email(), name, LocalDateTime.now())));
			socialAccountRepository.save(SocialAccount.link(user, provider, info.providerUserId(), info.email()));
			return authService.issueLoginTokens(user);
		} catch (DataIntegrityViolationException e) {
			return socialAccountRepository.findByProviderAndProviderUserId(provider, info.providerUserId())
					.map(account -> authService.issueLoginTokens(account.getUser()))
					.orElseThrow(() -> e);
		}
	}

	private OAuthUserInfo verify(SocialProvider provider, String token) {
		SocialTokenVerifier verifier = verifiers.stream()
				.filter(v -> v.provider() == provider)
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID));
		return verifier.verify(token);
	}
}
