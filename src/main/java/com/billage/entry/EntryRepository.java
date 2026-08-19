package com.billage.entry;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryRepository extends JpaRepository<Entry, Long> {

	/**
	 * 장부의 내역 목록. type·status·keyword 는 선택값이며 null 이면 조건에서 제외된다.
	 * keyword 는 제목과 메모를 함께 검색한다.
	 */
	@Query("""
			select e from Entry e
			where e.ledger.id = :ledgerId
			  and (:type is null or e.type = :type)
			  and (:status is null or e.approvalStatus = :status)
			  and (:keyword is null or e.title like concat('%', :keyword, '%')
			       or e.memo like concat('%', :keyword, '%'))
			""")
	Page<Entry> search(@Param("ledgerId") Long ledgerId,
			@Param("type") EntryType type,
			@Param("status") ApprovalStatus status,
			@Param("keyword") String keyword,
			Pageable pageable);

	/** 장부별 승인된 내역의 유형별 합계. */
	@Query("""
			select e.type, coalesce(sum(e.amount), 0)
			from Entry e
			where e.ledger.id = :ledgerId and e.approvalStatus = com.billage.entry.ApprovalStatus.APPROVED
			group by e.type
			""")
	List<Object[]> sumApprovedByTypeRaw(@Param("ledgerId") Long ledgerId);

	default Map<EntryType, Long> sumApprovedByType(Long ledgerId) {
		return sumApprovedByTypeRaw(ledgerId).stream()
				.collect(Collectors.toMap(row -> (EntryType) row[0], row -> (Long) row[1]));
	}

	@Query("select count(e.id) from Entry e where e.ledger.id = :ledgerId")
	long countByLedgerId(@Param("ledgerId") Long ledgerId);

	/** 모임 전체의 승인된 내역 유형별 합계(대시보드용). */
	@Query("""
			select e.type, coalesce(sum(e.amount), 0)
			from Entry e
			where e.groupId = :groupId and e.approvalStatus = com.billage.entry.ApprovalStatus.APPROVED
			group by e.type
			""")
	List<Object[]> sumApprovedByTypeForGroupRaw(@Param("groupId") Long groupId);

	default Map<EntryType, Long> sumApprovedByTypeForGroup(Long groupId) {
		return sumApprovedByTypeForGroupRaw(groupId).stream()
				.collect(Collectors.toMap(row -> (EntryType) row[0], row -> (Long) row[1]));
	}

	@Query("select count(e.id) from Entry e where e.groupId = :groupId and e.approvalStatus = :status")
	long countByGroupIdAndStatus(@Param("groupId") Long groupId, @Param("status") ApprovalStatus status);

	/** 모임 최근 내역. 장부명을 함께 쓰므로 fetch join 으로 N+1 을 피한다. */
	@Query("""
			select e from Entry e
			join fetch e.ledger
			where e.groupId = :groupId
			order by e.occurredOn desc, e.id desc
			""")
	List<Entry> findRecentByGroupId(@Param("groupId") Long groupId, Pageable pageable);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Entry e where e.ledger.id = :ledgerId")
	void deleteAllByLedgerId(@Param("ledgerId") Long ledgerId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Entry e where e.groupId = :groupId")
	void deleteAllByGroupId(@Param("groupId") Long groupId);
}
