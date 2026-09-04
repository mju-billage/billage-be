package com.billage.auth.email;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 인증 코드 행을 만들거나 갱신하는 한 트랜잭션.
 *
 * <p>{@link EmailVerificationService} 와 분리한 이유는 재시도 때문이다. 처음 보내는 이메일에 두 요청이
 * 동시에 들어오면 둘 다 "없음"을 보고 삽입해 유니크 제약에 걸리는데, 그 예외를 <b>같은 트랜잭션 안에서</b>
 * 잡아 이어갈 수는 없다(세션이 이미 롤백 표시라 이후 쿼리가 다 실패한다). 트랜잭션을 여기서 끝내고
 * 바깥에서 한 번 더 부르면, 두 번째에는 상대가 만든 행을 읽어 정상적으로 재발송한다.
 */
@Component
@RequiredArgsConstructor
class EmailVerificationIssuer {

	private final EmailVerificationRepository repository;
	private final EmailVerificationProperties properties;

	@Transactional
	EmailVerification issue(String email, String codeHash, LocalDateTime expiresAt, LocalDateTime now) {
		// 먼저 잠그지 않고 본다. 없는 행에 SELECT ... FOR UPDATE 를 걸면 갭 락이 잡혀,
		// 처음 보내는 이메일에 동시 요청이 오면 두 삽입이 서로를 기다리다 데드락이 난다.
		if (repository.findByEmail(email).isEmpty()) {
			return repository.saveAndFlush(EmailVerification.issue(email, codeHash, expiresAt, now));
		}
		// 이미 있는 행을 고칠 때만 잠근다 — 발송 횟수 누적이 덮어써지면 안 된다.
		EmailVerification found = repository.findByEmailForUpdate(email)
				.orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));
		return reissue(found, codeHash, expiresAt, now);
	}

	private EmailVerification reissue(EmailVerification verification, String codeHash, LocalDateTime expiresAt,
			LocalDateTime now) {
		if (verification.sendLimitExceeded(now, properties.sendWindowMinutes(), properties.maxSends())) {
			throw new BusinessException(ErrorCode.VERIFICATION_SEND_LIMIT_EXCEEDED);
		}
		verification.reissue(codeHash, expiresAt, now, properties.sendWindowMinutes());
		return verification;
	}
}
