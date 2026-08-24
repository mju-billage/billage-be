package com.billage.file;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileRepository extends JpaRepository<UploadedFile, Long> {

	/**
	 * 모임 대표 이미지 선점. 아직 임자가 없는 내 GROUP_IMAGE 파일에만 주인을 적는다.
	 * 조건부 UPDATE 한 줄이라 원자적이다 — 두 요청이 같은 파일을 노려도 한쪽만 1을 돌려받는다.
	 * (모임 쪽에 파일 ID 를 두는 구조였다면 두 테이블에 걸친 확인이 필요해 잠금 없이는 막을 수 없다.)
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update UploadedFile f set f.groupId = :groupId
			where f.id = :fileId and f.groupId is null and f.entry is null
			  and f.purpose = com.billage.file.FilePurpose.GROUP_IMAGE and f.uploadedBy = :userId
			""")
	int claimGroupImage(@Param("fileId") Long fileId, @Param("groupId") Long groupId,
			@Param("userId") Long userId);

	/** 모임의 현재 대표 이미지. */
	@Query("select f from UploadedFile f where f.groupId = :groupId")
	Optional<UploadedFile> findGroupImage(@Param("groupId") Long groupId);

	/** 여러 모임의 대표 이미지를 한 번에. 목록 조회의 N+1 을 피한다. */
	@Query("select f from UploadedFile f where f.groupId in :groupIds")
	List<UploadedFile> findGroupImages(@Param("groupIds") List<Long> groupIds);

	@Query("select f from UploadedFile f where f.entry.id = :entryId order by f.id asc")
	List<UploadedFile> findByEntryId(@Param("entryId") Long entryId);

	@Query("select f.entry.id, count(f.id) from UploadedFile f where f.entry.id in :entryIds group by f.entry.id")
	List<Object[]> countByEntryIdsRaw(@Param("entryIds") List<Long> entryIds);

	/** 내역이 삭제될 때 함께 지울 증빙 파일. 저장소 객체도 지워야 하므로 엔티티로 가져온다. */
	@Query("select f from UploadedFile f where f.entry.ledger.id = :ledgerId")
	List<UploadedFile> findByLedgerId(@Param("ledgerId") Long ledgerId);

	/** 모임에 달린 증빙 전체. 대표 이미지는 여기에 걸리지 않는다(내역에 연결되지 않으므로). */
	@Query("select f from UploadedFile f where f.entry.groupId = :groupId")
	List<UploadedFile> findReceiptsByGroupId(@Param("groupId") Long groupId);
}
