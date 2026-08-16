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

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Entry e where e.ledger.id = :ledgerId")
	void deleteAllByLedgerId(@Param("ledgerId") Long ledgerId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Entry e where e.groupId = :groupId")
	void deleteAllByGroupId(@Param("groupId") Long groupId);
}
