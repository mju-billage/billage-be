package com.billage.auth.email;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.auth.email.dto.EmailVerificationConfirmResponse;
import com.billage.auth.email.dto.EmailVerificationSendResponse;
import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.mail.MailSender;
import com.billage.common.response.KoreanTime;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원가입 이메일 인증(화면 COM-4-PAGE-01-0). 6자리 숫자 코드를 메일로 보내고 3분 안에 확인받는다.
 *
 * <p>코드는 해시로만 저장하고 응답에도 넣지 않는다 — 메일을 받은 사람만 알 수 있어야 인증이 성립한다.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int CODE_BOUND = 1_000_000;

	private final EmailVerificationRepository repository;
	private final UserRepository userRepository;
	private final MailSender mailSender;
	private final PasswordEncoder passwordEncoder;
	private final EmailVerificationVerifier verifier;
	private final EmailVerificationIssuer issuer;
	private final EmailVerificationProperties properties;

	/**
	 * 코드 발송. 재전송도 이 메서드를 다시 부르며, 그때마다 이전 코드는 무효가 되고 3분이 새로 시작된다.
	 *
	 * <p>트랜잭션은 {@link EmailVerificationIssuer} 안에서 끝난다 — 동시 삽입 충돌을 여기서 한 번 더
	 * 시도해 넘기기 위해서다. 메일은 그 트랜잭션이 커밋된 뒤에 나가므로, 저장에 실패한 코드가 발송되지 않는다.
	 */
	public EmailVerificationSendResponse send(String rawEmail) {
		String email = normalize(rawEmail);
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime expiresAt = now.plusSeconds(properties.ttlSeconds());
		String code = generateCode();

		String codeHash = passwordEncoder.encode(code);
		EmailVerification verification;
		try {
			verification = issuer.issue(email, codeHash, expiresAt, now);
		} catch (DataIntegrityViolationException | CannotAcquireLockException e) {
			// 동시에 들어온 다른 요청이 방금 이 이메일의 행을 만들었다(중복 키), 또는 두 삽입이 맞물렸다(데드락).
			// 두 번째 시도에서는 상대가 만든 행이 보이므로 정상적으로 재발송된다.
			verification = issuer.issue(email, codeHash, expiresAt, now);
		}

		mailSender.send(email, "[빌리지] 이메일 인증 코드", body(code));

		return new EmailVerificationSendResponse(email, KoreanTime.toOffset(verification.getExpiresAt()),
				properties.ttlSeconds());
	}

	/**
	 * 코드 확인. 성공하면 이 이메일은 인증된 것으로 남아 가입 때 쓰인다.
	 *
	 * <p>대조 자체는 {@link EmailVerificationVerifier} 가 트랜잭션 안에서 값으로 처리하고, 예외는 그 밖에서 던진다 —
	 * 트랜잭션 안에서 던지면 실패 시도 횟수 증가까지 롤백되어 시도 제한이 사라진다.
	 */
	public EmailVerificationConfirmResponse confirm(String rawEmail, String code) {
		String email = normalize(rawEmail);
		var result = verifier.check(email, code, properties.maxAttempts());

		switch (result.outcome()) {
			case NOT_FOUND -> throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
			case EXPIRED -> throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED);
			case ATTEMPT_EXCEEDED -> throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);
			case MISMATCH -> throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH);
			case SUCCESS -> {
				return new EmailVerificationConfirmResponse(email, true,
						KoreanTime.toOffset(result.verifiedAt()));
			}
		}
		throw new BusinessException(ErrorCode.INTERNAL_ERROR);
	}

	/**
	 * 가입 시 인증 여부 확인. {@code billage.auth.email-verification.required-for-signup} 이 꺼져 있으면
	 * 통과시킨다 — 프론트가 인증 화면을 붙이기 전까지 기존 가입 흐름을 깨지 않기 위해서다.
	 */
	@Transactional(readOnly = true)
	public void requireVerified(String rawEmail) {
		if (!properties.requiredForSignup()) {
			return;
		}
		boolean verified = repository.findByEmail(normalize(rawEmail))
				.map(EmailVerification::isVerified)
				.orElse(false);
		if (!verified) {
			throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
		}
	}

	private String normalize(String email) {
		return email.trim().toLowerCase();
	}

	private String generateCode() {
		return "%06d".formatted(RANDOM.nextInt(CODE_BOUND));
	}

	private String body(String code) {
		return """
				인증 코드는 %s 입니다.
				%d분 안에 앱에 입력해 주세요.

				본인이 요청한 것이 아니라면 이 메일을 무시하셔도 됩니다.
				""".formatted(code, properties.ttlSeconds() / 60);
	}
}
