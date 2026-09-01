package com.billage.membership;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

	Optional<GroupInvitation> findByCode(String code);

	/** 아직 살아 있는 코드 중 가장 나중까지 유효한 것. 멱등 발급이 이 값을 그대로 돌려준다. */
	Optional<GroupInvitation> findFirstByGroupIdAndExpiresAtAfterOrderByExpiresAtDesc(Long groupId,
			LocalDateTime at);

	boolean existsByCode(String code);

	void deleteByGroupId(Long groupId);
}
