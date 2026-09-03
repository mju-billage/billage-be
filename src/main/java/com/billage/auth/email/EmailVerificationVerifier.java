package com.billage.auth.email;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 코드 대조만 담당한다. 예외를 던지지 않고 결과를 값으로 돌려주는 것이 핵심이다 —
 * 트랜잭션 안에서 예외를 던지면 <b>실패 시도 횟수 증가까지 함께 롤백되어</b> 무차별 대입 제한이 무력해진다.
 *
 * <p>{@code REQUIRES_NEW} 로 따로 커밋하는 방법도 있지만, 이 행은 바깥 트랜잭션이 이미 잠그고 있어
 * 자기 자신을 기다리는 교착이 된다. 그래서 아예 예외를 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
class EmailVerificationVerifier {

	private final EmailVerificationRepository repository;
	private final PasswordEncoder passwordEncoder;

	enum Outcome {
		SUCCESS,
		NOT_FOUND,
		EXPIRED,
		ATTEMPT_EXCEEDED,
		MISMATCH
	}

	record Result(Outcome outcome, LocalDateTime verifiedAt) {

		static Result of(Outcome outcome) {
			return new Result(outcome, null);
		}
	}

	@Transactional
	Result check(String email, String code, int maxAttempts) {
		Optional<EmailVerification> found = repository.findByEmailForUpdate(email);
		if (found.isEmpty()) {
			return Result.of(Outcome.NOT_FOUND);
		}

		EmailVerification verification = found.get();
		LocalDateTime now = LocalDateTime.now();
		if (verification.isExpired(now)) {
			return Result.of(Outcome.EXPIRED);
		}
		if (verification.attemptLimitExceeded(maxAttempts)) {
			return Result.of(Outcome.ATTEMPT_EXCEEDED);
		}
		if (!passwordEncoder.matches(code, verification.getCodeHash())) {
			// 커밋돼야 하는 변경이다. 이 메서드가 예외를 던지지 않는 이유가 여기에 있다.
			verification.addAttempt();
			return Result.of(Outcome.MISMATCH);
		}

		verification.markVerified(now);
		return new Result(Outcome.SUCCESS, verification.getVerifiedAt());
	}
}
