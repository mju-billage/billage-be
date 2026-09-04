package com.billage.archive;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArchiveRepository extends JpaRepository<Archive, Long> {

	/** 보관함 목록. 최신 보관이 위로 온다. 카드가 요약값만 쓰므로 자식 스냅샷은 읽지 않는다. */
	@Query("select a from Archive a where a.groupId = :groupId order by a.id desc")
	List<Archive> findAllByGroupId(@Param("groupId") Long groupId);

	@Query("select a from Archive a left join fetch a.ledgers where a.id = :archiveId")
	Optional<Archive> findWithLedgers(@Param("archiveId") Long archiveId);
}
