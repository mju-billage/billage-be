package com.billage.folder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FolderRepository extends JpaRepository<Folder, Long> {

	/** 모임의 전체 폴더. 트리는 Service 에서 메모리로 조립한다(모임당 폴더 수가 많지 않다). */
	@Query("select f from Folder f where f.group.id = :groupId order by f.name asc")
	List<Folder> findAllByGroupId(@Param("groupId") Long groupId);

	// 엔티티에 getParentId()/getFolderId() 같은 편의 게터가 있어 파생 쿼리가 잘못된 경로로 해석되므로
	// 연관 경로를 쓰는 조회는 JPQL 로 명시한다.
	@Query("select f from Folder f where f.parent.id = :parentId")
	List<Folder> findChildren(@Param("parentId") Long parentId);

	/**
	 * 한 계층의 폴더. {@code parentId} 가 null 이면 최상위 영역의 폴더를 돌려준다.
	 *
	 * <p>JPQL 에서 {@code f.parent.id = null} 은 항상 거짓이라 {@code is null} 분기가 따로 필요하다.
	 */
	@Query("""
			select f from Folder f
			where f.group.id = :groupId
			  and ((:parentId is null and f.parent is null) or f.parent.id = :parentId)
			  and (:keyword is null or f.name like concat('%', :keyword, '%'))
			order by f.name asc
			""")
	List<Folder> findInLevel(@Param("groupId") Long groupId, @Param("parentId") Long parentId,
			@Param("keyword") String keyword);

	/** 폴더별 하위 폴더 수. 폴더 목록의 '{N}개의 항목' 을 N+1 없이 채우기 위해 한 번에 조회한다. */
	@Query("select f.parent.id, count(f.id) from Folder f where f.group.id = :groupId and f.parent is not null group by f.parent.id")
	List<Object[]> countByParentRaw(@Param("groupId") Long groupId);

	default Map<Long, Long> countByParent(Long groupId) {
		return countByParentRaw(groupId).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
	}

	/** 자기 참조 FK 가 있어 자식 폴더부터 삭제해야 한다. */
	default void deleteDeepestFirst(List<Folder> folders) {
		List<Folder> ordered = new ArrayList<>(folders);
		ordered.sort(Comparator.comparingInt(Folder::depth).reversed());
		deleteAll(ordered);
		flush();
	}
}
