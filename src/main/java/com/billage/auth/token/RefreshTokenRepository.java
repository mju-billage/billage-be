package com.billage.auth.token;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	/**
	 * 재발급·로그아웃 처리를 위해 비관적 쓰기 락으로 토큰을 조회한다.
	 * 동시에 같은 토큰으로 재발급을 시도하면 한 트랜잭션만 진행되고 나머지는 대기한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * 재사용 감지 시 같은 familyId의 아직 폐기되지 않은 토큰을 모두 REUSED로 폐기한다.
	 *
	 * @return 폐기된 행 수
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update RefreshToken t
			   set t.revokedAt = :now,
			       t.revokeReason = com.billage.auth.token.RevokeReason.REUSED
			 where t.familyId = :familyId
			   and t.revokedAt is null
			""")
	int revokeActiveFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);
}
