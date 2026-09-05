package com.billage.auth;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.auth.dto.AgreementsRequest;
import com.billage.auth.dto.LoginResponse;
import com.billage.auth.dto.SignupRequest;
import com.billage.auth.dto.SignupResponse;
import com.billage.auth.dto.TokenResponse;
import com.billage.auth.jwt.JwtProperties;
import com.billage.auth.jwt.JwtTokenProvider;
import com.billage.auth.token.RefreshToken;
import com.billage.auth.token.RefreshTokenGenerator;
import com.billage.auth.token.RefreshTokenRepository;
import com.billage.auth.token.TokenHasher;
import com.billage.auth.email.EmailVerificationService;
import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.user.User;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final JwtProperties jwtProperties;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final TokenHasher tokenHasher;
	private final EmailVerificationService emailVerificationService;
	private final TermsProperties termsProperties;

	private record IssuedRefreshToken(RefreshToken entity, String rawToken) {
	}

	/**
	 * 이메일 회원가입. 명세상 가입과 로그인은 분리돼 있어 토큰을 발급하지 않는다.
	 *
	 * <p>소셜 전용으로 먼저 만들어진 계정도 같은 이메일을 점유하므로 중복으로 막는다.
	 * 이 경우 비밀번호를 나중에 붙여주는 경로는 명세에 없다.
	 */
	@Transactional
	public SignupResponse signup(SignupRequest request) {
		String email = request.email();
		// 인증 요구는 설정으로 켠다 — 프론트가 인증 화면을 붙이기 전까지는 꺼 둔 채로 둔다.
		emailVerificationService.requireVerified(email);
		AgreementsRequest agreements = requireAgreements(request.agreements());
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}

		try {
			// 위 검사와 저장 사이에 같은 이메일이 끼어들 수 있다. 최종 판정은 users.email 의 UNIQUE 제약이며,
			// 지금 flush 해서 그 충돌을 이 자리에서 409 로 바꾼다(트랜잭션 커밋 시점에 터지면 500 이 된다).
			User user = User.create(email, passwordEncoder.encode(request.password()), request.name().trim());
			if (agreements != null) {
				LocalDateTime now = LocalDateTime.now();
				user.agreeToTermsIfNeeded(now);
				user.recordMarketingAgreement(agreements.marketingAgreed(), now);
			}
			return SignupResponse.from(userRepository.saveAndFlush(user));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
	}

	/**
	 * 약관 동의 검증. 값이 오면 언제나 필수 3종을 확인하고, 아예 오지 않은 요청을 허용할지는 설정이 정한다 —
	 * 프론트가 동의 화면을 붙이기 전에 필수로 켜면 기존 가입 흐름이 곧바로 막히기 때문이다.
	 */
	private AgreementsRequest requireAgreements(AgreementsRequest agreements) {
		if (agreements == null) {
			if (termsProperties.requiredForSignup()) {
				throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
			}
			return null;
		}
		if (!agreements.allRequiredAgreed()) {
			throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
		}
		return agreements;
	}

	/**
	 * 이메일·비밀번호 로그인. 실패 원인(미존재/불일치)을 구분하지 않고 동일 오류로 응답한다.
	 */
	@Transactional
	public LoginResponse login(String email, String rawPassword) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
		// 소셜 전용 계정은 비밀번호가 없다 — matches()에 null을 넘기면 예외가 나므로 먼저 걸러낸다.
		if (user.getPassword() == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		return issueLoginTokens(user);
	}

	/**
	 * 새 로그인 세션의 토큰 쌍을 발급한다. 이메일 로그인·소셜 로그인이 공통으로 사용한다.
	 */
	@Transactional
	public LoginResponse issueLoginTokens(User user) {
		String familyId = UUID.randomUUID().toString();
		IssuedRefreshToken issued = issueRefreshToken(user, familyId, null, LocalDateTime.now());
		String accessToken = jwtTokenProvider.createAccessToken(user.getId());

		return LoginResponse.of(user, accessToken, issued.rawToken(),
				jwtTokenProvider.accessTokenExpiresInSeconds());
	}

	/**
	 * Refresh Token 재발급. 전체 과정을 하나의 트랜잭션으로 처리하며,
	 * 재사용 감지 시 {@link RefreshTokenReuseException}을 던져 패밀리 폐기를 커밋한다.
	 */
	@Transactional(noRollbackFor = RefreshTokenReuseException.class)
	public TokenResponse refresh(String rawRefreshToken) {
		LocalDateTime now = LocalDateTime.now();
		String tokenHash = tokenHasher.hash(rawRefreshToken);

		// 비관적 쓰기 락으로 조회 → 동시 재발급 시 하나만 진행
		RefreshToken current = refreshTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

		// 이미 폐기(회전 포함)된 토큰이 다시 제출됨 → 재사용 감지.
		// 같은 familyId의 아직 살아있는 토큰을 모두 REUSED로 폐기한다.
		// (current 자신은 이미 폐기 상태이므로 기존 사유를 그대로 두어 이력을 보존한다.)
		if (current.isRevoked()) {
			refreshTokenRepository.revokeActiveFamily(current.getFamilyId(), now);
			throw new RefreshTokenReuseException();
		}
		if (current.isExpired(now)) {
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
		}

		User user = current.getUser();
		IssuedRefreshToken issued = issueRefreshToken(user, current.getFamilyId(), current.getDeviceId(), now);
		current.markUsed(now);
		current.rotate(issued.entity().getId(), now);

		String accessToken = jwtTokenProvider.createAccessToken(user.getId());
		return TokenResponse.of(accessToken, issued.rawToken(),
				jwtTokenProvider.accessTokenExpiresInSeconds());
	}

	/**
	 * 로그아웃. 존재하지 않거나 이미 폐기된 토큰이어도 성공하는 멱등 처리.
	 */
	@Transactional
	public void logout(String rawRefreshToken) {
		String tokenHash = tokenHasher.hash(rawRefreshToken);
		refreshTokenRepository.findByTokenHash(tokenHash)
				.ifPresent(token -> token.revokeForLogout(LocalDateTime.now()));
	}

	private IssuedRefreshToken issueRefreshToken(User user, String familyId, String deviceId, LocalDateTime now) {
		String rawToken = refreshTokenGenerator.generate();
		String tokenHash = tokenHasher.hash(rawToken);
		LocalDateTime expiresAt = now.plus(jwtProperties.refreshTokenValidity());
		RefreshToken saved = refreshTokenRepository.save(
				RefreshToken.issue(user, tokenHash, familyId, deviceId, expiresAt));
		return new IssuedRefreshToken(saved, rawToken);
	}
}
