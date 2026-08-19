package com.billage.dues;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DuesRepository extends JpaRepository<Dues, Long> {

	/** 회비 목록. status 는 선택값이며 null 이면 조건에서 제외된다. */
	@Query("select d from Dues d where d.groupId = :groupId and (:status is null or d.status = :status)")
	Page<Dues> search(@Param("groupId") Long groupId, @Param("status") DuesStatus status, Pageable pageable);

	/** 대시보드용. 진행 중인 회비 수. */
	long countByGroupIdAndStatus(Long groupId, DuesStatus status);

	/** 모임 삭제용. 대상자(dues_member)는 cascade + orphanRemoval 로 함께 지워진다. */
	List<Dues> findAllByGroupId(Long groupId);
}
