package com.billage.folder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

	/** 자기 참조 FK 가 있어 자식 폴더부터 삭제해야 한다. */
	default void deleteDeepestFirst(List<Folder> folders) {
		List<Folder> ordered = new ArrayList<>(folders);
		ordered.sort(Comparator.comparingInt(Folder::depth).reversed());
		deleteAll(ordered);
		flush();
	}
}
