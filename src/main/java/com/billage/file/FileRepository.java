package com.billage.file;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileRepository extends JpaRepository<UploadedFile, Long> {

	@Query("select f from UploadedFile f where f.entry.id = :entryId order by f.id asc")
	List<UploadedFile> findByEntryId(@Param("entryId") Long entryId);

	@Query("select f.entry.id, count(f.id) from UploadedFile f where f.entry.id in :entryIds group by f.entry.id")
	List<Object[]> countByEntryIdsRaw(@Param("entryIds") List<Long> entryIds);

	/** 내역이 삭제될 때 함께 지울 증빙 파일. 저장소 객체도 지워야 하므로 엔티티로 가져온다. */
	@Query("select f from UploadedFile f where f.entry.ledger.id = :ledgerId")
	List<UploadedFile> findByLedgerId(@Param("ledgerId") Long ledgerId);

	@Query("select f from UploadedFile f where f.entry.groupId = :groupId")
	List<UploadedFile> findByGroupId(@Param("groupId") Long groupId);
}
