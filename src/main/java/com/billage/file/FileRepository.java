package com.billage.file;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.billage.entry.EntryType;

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

	/**
	 * 아직 어디에도 쓰이지 않을 때만 지운다. 조건부 DELETE 라 삭제와 선점이 같은 조건을 두고 원자적으로 겨룬다 —
	 * "안 쓰인다"를 먼저 읽고 나중에 지우면 그 사이에 선점된 파일까지 지워 버린다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from UploadedFile f where f.id = :fileId and f.groupId is null and f.userId is null "
			+ "and f.entry is null")
	int deleteIfUnused(@Param("fileId") Long fileId);

	/**
	 * 모임에서 대표 이미지를 떼어낸다. 파일 자체는 남는다.
	 * 방금 읽은 그 파일일 때만 떼어낸다 — 조건 없이 떼면, 동시에 들어온 다른 교체가 이미 붙여 놓은
	 * 새 이미지를 떼어 버려 주인 없는 파일이 남는다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update UploadedFile f set f.groupId = null where f.groupId = :groupId and f.id = :fileId")
	int detachGroupImage(@Param("groupId") Long groupId, @Param("fileId") Long fileId);

	/** 모임의 현재 대표 이미지. */
	@Query("select f from UploadedFile f where f.groupId = :groupId")
	Optional<UploadedFile> findGroupImage(@Param("groupId") Long groupId);

	/**
	 * 프로필 이미지 선점. 대표 이미지({@link #claimGroupImage})와 같은 방식이며 주인만 사용자로 바뀐다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update UploadedFile f set f.userId = :userId
			where f.id = :fileId and f.userId is null and f.groupId is null and f.entry is null
			  and f.purpose = com.billage.file.FilePurpose.PROFILE_IMAGE and f.uploadedBy = :userId
			""")
	int claimProfileImage(@Param("fileId") Long fileId, @Param("userId") Long userId);

	/** 사용자에게서 프로필 이미지를 떼어낸다. 파일 자체는 남는다(교체 중간 단계). */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update UploadedFile f set f.userId = null where f.userId = :userId and f.id = :fileId")
	int detachProfileImage(@Param("userId") Long userId, @Param("fileId") Long fileId);

	/** 사용자의 현재 프로필 이미지. */
	@Query("select f from UploadedFile f where f.userId = :userId")
	Optional<UploadedFile> findProfileImage(@Param("userId") Long userId);

	/**
	 * 탈퇴한 사용자가 올린 파일의 업로더 표시를 지운다. 파일은 남는다 —
	 * 증빙은 올린 사람의 것이 아니라 그 모임의 회계 이력이다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update UploadedFile f set f.uploadedBy = null where f.uploadedBy = :userId")
	int clearUploader(@Param("userId") Long userId);

	/** 여러 모임의 대표 이미지를 한 번에. 목록 조회의 N+1 을 피한다. */
	@Query("select f from UploadedFile f where f.groupId in :groupIds")
	List<UploadedFile> findGroupImages(@Param("groupIds") List<Long> groupIds);

	@Query("select f from UploadedFile f where f.entry.id = :entryId order by f.id asc")
	List<UploadedFile> findByEntryId(@Param("entryId") Long entryId);

	/** 여러 내역에 붙은 증빙을 한 번에. 기록 보관이 주인을 옮길 때 쓴다. */
	@Query("select f from UploadedFile f where f.entry.id in :entryIds order by f.id asc")
	List<UploadedFile> findByEntryIds(@Param("entryIds") Collection<Long> entryIds);

	/** 보관된 내역들에 붙은 증빙. 보관 상세가 이미지를 함께 보여 줄 때 쓴다. */
	@Query("select f from UploadedFile f where f.archiveEntry.id in :archiveEntryIds order by f.id asc")
	List<UploadedFile> findByArchiveEntryIds(@Param("archiveEntryIds") Collection<Long> archiveEntryIds);

	/** 보관 기록을 지울 때 함께 지울 증빙. */
	@Query("select f from UploadedFile f where f.archiveEntry.archiveLedger.archive.id = :archiveId")
	List<UploadedFile> findByArchiveId(@Param("archiveId") Long archiveId);

	/**
	 * 증빙 상한 검사용 <b>잠금 읽기</b>. 일반 읽기는 트랜잭션 스냅샷을 보므로 동시 요청이 서로의
	 * 증빙을 세지 못해 10장을 넘길 수 있다({@code GroupInvitationRepository.findValidForUpdate} 와 같은 이유).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from UploadedFile f where f.entry.id = :entryId")
	List<UploadedFile> findByEntryIdForUpdate(@Param("entryId") Long entryId);

	@Query("select f.entry.id, count(f.id) from UploadedFile f where f.entry.id in :entryIds group by f.entry.id")
	List<Object[]> countByEntryIdsRaw(@Param("entryIds") List<Long> entryIds);

	/** 내역이 삭제될 때 함께 지울 증빙 파일. 저장소 객체도 지워야 하므로 엔티티로 가져온다. */
	@Query("select f from UploadedFile f where f.entry.ledger.id = :ledgerId")
	List<UploadedFile> findByLedgerId(@Param("ledgerId") Long ledgerId);

	/** 모임에 달린 증빙 전체. 대표 이미지는 여기에 걸리지 않는다(내역에 연결되지 않으므로). */
	@Query("select f from UploadedFile f where f.entry.groupId = :groupId")
	List<UploadedFile> findReceiptsByGroupId(@Param("groupId") Long groupId);

	/**
	 * 증빙자료 앨범. 모임의 증빙을 원본 내역과 함께 최신 발생일 순으로 모아 본다.
	 *
	 * <p>필터 조건은 내역 목록과 같은 것을 쓴다 — 화면도 "내역 조회 공통 로직과 완벽하게 동일하게 작동"이라
	 * 적어 두었다. 앨범 항목이 내역명·장부명을 함께 보여 주므로 fetch join 으로 N+1 을 피한다.
	 *
	 * <p>모임 대표 이미지는 내역에 연결되지 않아 여기에 걸리지 않는다.
	 */
	@Query(value = """
			select f from UploadedFile f
			join fetch f.entry e
			join fetch e.ledger l
			where e.groupId = :groupId
			  and (:ledgerIds is null or l.id in :ledgerIds)
			  and (:type is null or e.type = :type)
			  and (:from is null or e.occurredOn >= :from)
			  and (:to is null or e.occurredOn <= :to)
			  and (:keyword is null or e.title like concat('%', :keyword, '%')
			       or l.name like concat('%', :keyword, '%'))
			order by e.occurredOn desc, e.id desc, f.id asc
			""",
			countQuery = """
			select count(f.id) from UploadedFile f
			where f.entry.groupId = :groupId
			  and (:ledgerIds is null or f.entry.ledger.id in :ledgerIds)
			  and (:type is null or f.entry.type = :type)
			  and (:from is null or f.entry.occurredOn >= :from)
			  and (:to is null or f.entry.occurredOn <= :to)
			  and (:keyword is null or f.entry.title like concat('%', :keyword, '%')
			       or f.entry.ledger.name like concat('%', :keyword, '%'))
			""")
	Page<UploadedFile> searchReceipts(@Param("groupId") Long groupId,
			@Param("ledgerIds") Collection<Long> ledgerIds,
			@Param("type") EntryType type,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			@Param("keyword") String keyword,
			Pageable pageable);
}
