package com.billage.ledger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerRepository extends JpaRepository<Ledger, Long> {

	// getFolderId() 편의 게터 때문에 파생 쿼리가 잘못 해석되므로 연관 경로 조회는 JPQL 로 명시한다.
	@Query("select l from Ledger l where l.folder.id = :folderId order by l.name asc")
	List<Ledger> findAllByFolderId(@Param("folderId") Long folderId);

	@Query("delete from Ledger l where l.group.id = :groupId")
	@org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
	void deleteAllByGroupId(@Param("groupId") Long groupId);

	@Query("select l.folder.id, count(l.id) from Ledger l where l.group.id = :groupId and l.folder is not null group by l.folder.id")
	List<Object[]> countByFolderRaw(@Param("groupId") Long groupId);

	/** 폴더별 장부 수. 폴더 목록에서 N+1 없이 ledgerCount 를 채우기 위해 한 번에 조회한다. */
	default Map<Long, Long> countByFolder(Long groupId) {
		return countByFolderRaw(groupId).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
	}
}
