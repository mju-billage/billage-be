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

	/**
	 * 모임의 모든 장부. 폴더 구조를 가로질러 평평하게 모은다 — 예산 설정 화면과 장부 선택 바텀시트들이 쓴다.
	 * 폴더에 속하지 않은(최상위 영역) 장부도 포함한다.
	 *
	 * <p>정렬은 화면명세의 「예산 설정」 규칙대로 <b>최신 생성순</b>이다.
	 * 폴더명을 함께 보여 주므로 {@code left join fetch} 로 N+1 을 피한다(폴더가 없을 수 있어 left).
	 */
	@Query("""
			select l from Ledger l
			left join fetch l.folder
			where l.group.id = :groupId
			  and (:keyword is null or l.name like concat('%', :keyword, '%'))
			order by l.createdAt desc, l.id desc
			""")
	List<Ledger> findAllInGroup(@Param("groupId") Long groupId, @Param("keyword") String keyword);

	/**
	 * 한 계층의 장부. {@code folderId} 가 null 이면 최상위 영역(어느 폴더에도 속하지 않은) 장부다 —
	 * 폴더를 해제하면 그 안의 장부가 여기로 올라온다.
	 */
	@Query("""
			select l from Ledger l
			where l.group.id = :groupId
			  and ((:folderId is null and l.folder is null) or l.folder.id = :folderId)
			  and (:keyword is null or l.name like concat('%', :keyword, '%'))
			order by l.name asc
			""")
	List<Ledger> findInLevel(@Param("groupId") Long groupId, @Param("folderId") Long folderId,
			@Param("keyword") String keyword);

	@Query("delete from Ledger l where l.group.id = :groupId")
	@org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
	void deleteAllByGroupId(@Param("groupId") Long groupId);

	@Query("select count(l.id) from Ledger l where l.group.id = :groupId")
	long countByGroupId(@Param("groupId") Long groupId);

	@Query("select l.folder.id, count(l.id) from Ledger l where l.group.id = :groupId and l.folder is not null group by l.folder.id")
	List<Object[]> countByFolderRaw(@Param("groupId") Long groupId);

	/** 폴더별 장부 수. 폴더 목록에서 N+1 없이 ledgerCount 를 채우기 위해 한 번에 조회한다. */
	default Map<Long, Long> countByFolder(Long groupId) {
		return countByFolderRaw(groupId).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
	}
}
