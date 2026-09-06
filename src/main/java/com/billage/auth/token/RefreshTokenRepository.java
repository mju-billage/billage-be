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

	/**
	 * 비밀번호 변경 시 다른 기기의 세션을 끊는다. 비밀번호를 바꾼 이유가 유출 의심일 수 있어
	 * 남아 있는 Refresh Token 을 그대로 두면 변경이 무의미해진다.
	 *
	 * @param keepFamilyId 지금 쓰고 있는 기기의 패밀리. null 이면 모든 기기를 끊는다(재로그인 필요).
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update RefreshToken t
			   set t.revokedAt = :now,
			       t.revokeReason = com.billage.auth.token.RevokeReason.PASSWORD_CHANGED
			 where t.user.id = :userId
			   and t.revokedAt is null
			   and (:keepFamilyId is null or t.familyId <> :keepFamilyId)
			""")
	int revokeActiveForUser(@Param("userId") Long userId, @Param("keepFamilyId") String keepFamilyId,
			@Param("now") LocalDateTime now);

	Optional<RefreshToken> findFirstByTokenHash(String tokenHash);

	/** 탈퇴 시 그 계정의 토큰을 전부 지운다. 계정과 함께 사라지는 데이터다. */
	void deleteByUserId(Long userId);
}
