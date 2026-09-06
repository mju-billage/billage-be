package com.billage.group;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface GroupSpaceRepository extends JpaRepository<GroupSpace, Long> {

	/**
	 * 모임 행을 잠그고 가져온다. 초대 코드 발급처럼 "없으면 만든다"를 모임 단위로 직렬화해야 할 때 쓴다 —
	 * 두 요청이 동시에 "유효한 코드 없음"을 보고 각자 코드를 만들면 멱등성이 깨진다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from GroupSpace g where g.id = :groupId")
	Optional<GroupSpace> findByIdForUpdate(@Param("groupId") Long groupId);

	/**
	 * 탈퇴한 사용자가 만든 모임에서 생성자 표시만 지운다. 모임은 남은 관리자들의 것이라 그대로 둔다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update GroupSpace g set g.createdBy = null where g.createdBy = :userId")
	int clearCreatedBy(@Param("userId") Long userId);
}
