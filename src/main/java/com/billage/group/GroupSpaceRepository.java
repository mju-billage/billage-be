package com.billage.group;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface GroupSpaceRepository extends JpaRepository<GroupSpace, Long> {

	/** 대표 이미지를 쓰고 있는 모임. 파일 열람 권한을 판정할 때 쓴다. */
	Optional<GroupSpace> findByGroupImageFileId(Long groupImageFileId);

	/**
	 * 같은 판정을 최신 커밋 기준으로 한다. 파일을 지울지·붙일지 결정하는 경로에서 쓴다 —
	 * MySQL REPEATABLE READ 에서 일반 조회는 트랜잭션 시작 스냅샷을 보므로,
	 * 파일 행을 잠그더라도 일반 조회로 확인하면 방금 붙은 참조를 놓친다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from GroupSpace g where g.groupImageFileId = :fileId")
	Optional<GroupSpace> findByGroupImageFileIdForUpdate(@Param("fileId") Long fileId);
}
