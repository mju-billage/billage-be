package com.billage.group;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupManagerRepository extends JpaRepository<GroupManager, Long> {

	Optional<GroupManager> findByGroupIdAndUserId(Long groupId, Long userId);

	boolean existsByGroupIdAndUserId(Long groupId, Long userId);

	/** 모임의 관리자 목록. 먼저 참여한 순서대로 보여준다(OWNER가 항상 첫 번째). */
	List<GroupManager> findByGroupIdOrderByCreatedAtAsc(Long groupId);

	/** "내 모임" 목록. 요약 응답에서 모임을 함께 쓰므로 fetch join으로 N+1을 피한다. */
	@Query("select gm from GroupManager gm join fetch gm.group"
			+ " where gm.userId = :userId order by gm.createdAt desc")
	List<GroupManager> findWithGroupByUserId(@Param("userId") Long userId);
}
