package com.billage.auth.email;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

	Optional<EmailVerification> findByEmail(String email);

	/**
	 * 발송·검증이 같은 행을 동시에 고칠 수 있어 잠금 읽기로 가져온다.
	 * 일반 읽기는 트랜잭션 스냅샷을 보므로 시도 횟수가 덮어써질 수 있다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from EmailVerification v where v.email = :email")
	Optional<EmailVerification> findByEmailForUpdate(@Param("email") String email);
}
