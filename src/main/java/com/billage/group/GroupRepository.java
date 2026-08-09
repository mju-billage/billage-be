package com.billage.group;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface GroupRepository extends JpaRepository<Group, Long> {

	boolean existsByInviteCode(String inviteCode);

	Optional<Group> findByInviteCode(String inviteCode);

	/** 초대 코드 참여 시 재발급과의 경쟁을 막으려고 비관적 락으로 조회한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from Group g where g.inviteCode = :inviteCode")
	Optional<Group> findByInviteCodeWithLock(@Param("inviteCode") String inviteCode);
}
