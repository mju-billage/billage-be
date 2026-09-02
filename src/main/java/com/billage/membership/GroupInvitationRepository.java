package com.billage.membership;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

	Optional<GroupInvitation> findByCode(String code);

	/** 아직 살아 있는 코드 중 가장 나중까지 유효한 것. 읽기 전용 경로가 쓴다. */
	Optional<GroupInvitation> findFirstByGroupIdAndExpiresAtAfterOrderByExpiresAtDesc(Long groupId,
			LocalDateTime at);

	/**
	 * 발급 직전에 쓰는 <b>잠금 읽기</b> 버전.
	 *
	 * <p>일반 읽기로는 안 된다 — MySQL 기본 격리 수준(REPEATABLE READ)의 일관된 읽기는 트랜잭션이
	 * 처음 읽은 시점의 스냅샷을 보므로, 앞선 요청이 방금 커밋한 코드가 보이지 않아 하나를 더 만든다.
	 * 잠금 읽기는 항상 최신 커밋본을 읽는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from GroupInvitation i where i.group.id = :groupId and i.expiresAt > :at "
			+ "order by i.expiresAt desc")
	List<GroupInvitation> findValidForUpdate(@Param("groupId") Long groupId, @Param("at") LocalDateTime at);

	boolean existsByCode(String code);

	void deleteByGroupId(Long groupId);
}
