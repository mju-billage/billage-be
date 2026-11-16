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

	/**
	 * 보관함마다 담긴 증빙 파일의 용량 합계. 증빙이 하나도 없는 보관함은 결과에 없다.
	 * 목록에서 보관함 수만큼 쿼리를 내지 않도록 한 번에 가져온다.
	 */
	@Query("""
			select l.archive.id as archiveId, sum(f.size) as sizeBytes
			  from UploadedFile f
			  join f.archiveEntry e
			  join e.archiveLedger l
			 where l.archive.groupId = :groupId
			 group by l.archive.id
			""")
	List<ArchiveSize> sumReceiptSizesByGroupId(@Param("groupId") Long groupId);

	interface ArchiveSize {
		Long getArchiveId();

		Long getSizeBytes();
	}

	@Query("select a from Archive a left join fetch a.ledgers where a.id = :archiveId")
	Optional<Archive> findWithLedgers(@Param("archiveId") Long archiveId);
}
