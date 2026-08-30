package com.billage.entry;

import java.time.LocalDate;
import java.util.Collection;
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

	/**
	 * 보고서 스냅샷용. 여러 장부의 기간 내 <b>승인된</b> 내역을 한 번에 읽는다(장부 수만큼 쿼리하지 않는다).
	 */
	@Query("""
			select e from Entry e
			where e.ledger.id in :ledgerIds
			  and e.approvalStatus = com.billage.entry.ApprovalStatus.APPROVED
			  and e.occurredOn between :startDate and :endDate
			order by e.occurredOn asc, e.id asc
			""")
	List<Entry> findApprovedInRange(@Param("ledgerIds") Collection<Long> ledgerIds,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/**
	 * 보고서 스냅샷용. 위와 같지만 <b>구분(수입/지출) 필터</b>가 붙는다 — 장부별 보고서가 "전체/수입/지출"을 고른다.
	 * {@code type} 이 null 이면 전체다.
	 */
	@Query("""
			select e from Entry e
			where e.ledger.id in :ledgerIds
			  and e.approvalStatus = com.billage.entry.ApprovalStatus.APPROVED
			  and (:type is null or e.type = :type)
			  and (:startDate is null or e.occurredOn >= :startDate)
			  and (:endDate is null or e.occurredOn <= :endDate)
			order by e.occurredOn asc, e.id asc
			""")
	List<Entry> findApprovedForReport(@Param("ledgerIds") Collection<Long> ledgerIds,
			@Param("type") EntryType type,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/**
	 * 기간별 보고서용. 그 기간에 승인된 내역이 <b>하나라도 있는</b> 장부 ID.
	 * 화면이 장부를 고르지 않으므로 서버가 대상 장부를 정한다.
	 */
	@Query("""
			select distinct e.ledger.id from Entry e
			where e.groupId = :groupId
			  and e.approvalStatus = com.billage.entry.ApprovalStatus.APPROVED
			  and e.occurredOn between :startDate and :endDate
			""")
	List<Long> findLedgerIdsWithApprovedEntries(@Param("groupId") Long groupId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/**
	 * 기간별 보고서의 '시작 잔액'. 기간 시작일 <b>직전까지</b> 누적된 승인 내역의 유형별 합계다.
	 */
	@Query("""
			select e.type, coalesce(sum(e.amount), 0)
			from Entry e
			where e.ledger.id in :ledgerIds
			  and e.approvalStatus = com.billage.entry.ApprovalStatus.APPROVED
			  and e.occurredOn < :before
			group by e.type
			""")
	List<Object[]> sumApprovedBeforeRaw(@Param("ledgerIds") Collection<Long> ledgerIds,
			@Param("before") LocalDate before);

	default Map<EntryType, Long> sumApprovedBefore(Collection<Long> ledgerIds, LocalDate before) {
		if (ledgerIds.isEmpty()) {
			return Map.of();
		}
		return sumApprovedBeforeRaw(ledgerIds, before).stream()
				.collect(Collectors.toMap(row -> (EntryType) row[0], row -> (Long) row[1]));
	}

	/** 장부별 보고서의 기간. 담긴 승인 내역의 실제 최소·최대 발생일이다. */
	@Query("""
			select min(e.occurredOn), max(e.occurredOn)
			from Entry e
			where e.ledger.id in :ledgerIds
			  and e.approvalStatus = com.billage.entry.ApprovalStatus.APPROVED
			  and (:type is null or e.type = :type)
			""")
	List<Object[]> findOccurredRangeRaw(@Param("ledgerIds") Collection<Long> ledgerIds,
			@Param("type") EntryType type);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Entry e where e.ledger.id = :ledgerId")
	void deleteAllByLedgerId(@Param("ledgerId") Long ledgerId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Entry e where e.groupId = :groupId")
	void deleteAllByGroupId(@Param("groupId") Long groupId);
}
