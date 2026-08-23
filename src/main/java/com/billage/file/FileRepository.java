package com.billage.file;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface FileRepository extends JpaRepository<UploadedFile, Long> {

	/**
	 * 파일 행을 잠근 채로 가져온다. "이 파일이 어딘가에 쓰이는가"를 확인하고 결정을 내리는 경로에서 쓴다.
	 * 잠그지 않으면 직접 삭제와 모임 대표 이미지 지정이 동시에 들어올 때 양쪽 다 "안 쓰이는 파일"로 보고
	 * 커밋해, 지워진 파일을 가리키는 모임이 남는다(V13 은 의도적으로 FK 를 걸지 않는다).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from UploadedFile f where f.id = :fileId")
	Optional<UploadedFile> findByIdForUpdate(@Param("fileId") Long fileId);

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
