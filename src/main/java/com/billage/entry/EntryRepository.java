package com.billage.entry;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface EntryRepository extends JpaRepository<Entry, Long> {

	/**
	 * 내역 행을 잠그고 가져온다. 증빙 상한처럼 "현재 상태를 세고 나서 바꾸는" 작업을
	 * 내역 단위로 직렬화할 때 쓴다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Entry e where e.id = :entryId")
	Optional<Entry> findByIdForUpdate(@Param("entryId") Long entryId);

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

	/**
	 * 모임 전체 내역 목록. GNB 「내역」 탭이 쓰며, 장부 하나에 갇힌 {@link #search} 와 달리
	 * <b>장부를 여러 개 고를 수 있고</b> 검색이 <b>장부명까지</b> 훑는다(화면명세 「내역 검색」).
	 *
	 * <p>모든 조건은 선택값이며 {@code null} 이면 조건에서 빠진다. {@code ledgerIds} 는 비어 있으면
	 * 모임의 모든 장부를 본다 — JPQL 의 {@code in} 은 빈 컬렉션을 받으면 문법 오류가 나므로
	 * 서비스가 {@code null} 로 바꿔 넘긴다.
	 *
	 * <p>목록은 장부명을 함께 보여 주므로 {@code join fetch} 로 N+1 을 피한다.
	 */
	@Query(value = """
			select e from Entry e
			join fetch e.ledger l
			where e.groupId = :groupId
			  and (:ledgerIds is null or l.id in :ledgerIds)
			  and (:type is null or e.type = :type)
			  and (:status is null or e.approvalStatus = :status)
			  and (:from is null or e.occurredOn >= :from)
			  and (:to is null or e.occurredOn <= :to)
			  and (:keyword is null or e.title like concat('%', :keyword, '%')
			       or l.name like concat('%', :keyword, '%'))
			""",
			countQuery = """
			select count(e.id) from Entry e
			where e.groupId = :groupId
			  and (:ledgerIds is null or e.ledger.id in :ledgerIds)
			  and (:type is null or e.type = :type)
			  and (:status is null or e.approvalStatus = :status)
			  and (:from is null or e.occurredOn >= :from)
			  and (:to is null or e.occurredOn <= :to)
			  and (:keyword is null or e.title like concat('%', :keyword, '%')
			       or e.ledger.name like concat('%', :keyword, '%'))
			""")
	Page<Entry> searchInGroup(@Param("groupId") Long groupId,
			@Param("ledgerIds") Collection<Long> ledgerIds,
			@Param("type") EntryType type,
			@Param("status") ApprovalStatus status,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			@Param("keyword") String keyword,
			Pageable pageable);

	/**
	 * 위 목록과 <b>같은 조건</b>으로 낸 승인 내역의 유형별 합계. 화면 상단 잔액 카드가 쓴다.
	 *
	 * <p>목록과 따로 세는 이유는 두 가지다 — 잔액은 페이지가 아니라 조건 전체를 대상으로 하고,
	 * 승인 대기 내역은 잔액에 넣지 않는다(승인된 내역만 회계에 반영한다는 공통 규칙).
	 * 그래서 {@code status} 조건은 받지 않는다.
	 */
	@Query("""
			select e.type, coalesce(sum(e.amount), 0)
			from Entry e
			where e.groupId = :groupId
			  and e.approvalStatus = com.billage.entry.ApprovalStatus.APPROVED
			  and (:ledgerIds is null or e.ledger.id in :ledgerIds)
			  and (:type is null or e.type = :type)
			  and (:from is null or e.occurredOn >= :from)
			  and (:to is null or e.occurredOn <= :to)
			  and (:keyword is null or e.title like concat('%', :keyword, '%')
			       or e.ledger.name like concat('%', :keyword, '%'))
			group by e.type
			""")
	List<Object[]> sumApprovedInGroupRaw(@Param("groupId") Long groupId,
			@Param("ledgerIds") Collection<Long> ledgerIds,
			@Param("type") EntryType type,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			@Param("keyword") String keyword);

	default Map<EntryType, Long> sumApprovedInGroup(Long groupId, Collection<Long> ledgerIds, EntryType type,
			LocalDate from, LocalDate to, String keyword) {
		return sumApprovedInGroupRaw(groupId, ledgerIds, type, from, to, keyword).stream()
				.collect(Collectors.toMap(row -> (EntryType) row[0], row -> (Long) row[1]));
	}

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

	/**
	 * 여러 장부의 승인 합계를 한 번에. 장부 목록 화면들이 장부마다 집계 쿼리를 돌리면
	 * 장부 수만큼 쿼리가 늘어난다(N+1).
	 */
	@Query("""
			select e.ledger.id, e.type, coalesce(sum(e.amount), 0)
			from Entry e
			where e.ledger.id in :ledgerIds
			  and e.approvalStatus = com.billage.entry.ApprovalStatus.APPROVED
			group by e.ledger.id, e.type
			""")
	List<Object[]> sumApprovedByLedgerRaw(@Param("ledgerIds") Collection<Long> ledgerIds);

	/** 장부 ID → (유형 → 합계). 내역이 없는 장부는 키 자체가 없으므로 호출부에서 기본값을 채운다. */
	default Map<Long, Map<EntryType, Long>> sumApprovedByLedger(Collection<Long> ledgerIds) {
		if (ledgerIds.isEmpty()) {
			return Map.of();
		}
		return sumApprovedByLedgerRaw(ledgerIds).stream()
				.collect(Collectors.groupingBy(row -> (Long) row[0],
						Collectors.toMap(row -> (EntryType) row[1], row -> (Long) row[2])));
	}

	@Query("select e.ledger.id, count(e.id) from Entry e where e.ledger.id in :ledgerIds group by e.ledger.id")
	List<Object[]> countByLedgersRaw(@Param("ledgerIds") Collection<Long> ledgerIds);

	/** 장부 ID → 전체 내역 수(승인 여부 무관). */
	default Map<Long, Long> countByLedgers(Collection<Long> ledgerIds) {
		if (ledgerIds.isEmpty()) {
			return Map.of();
		}
		return countByLedgersRaw(ledgerIds).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
	}

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

	/**
	 * 일자별 승인 내역 합계. 대시보드의 최근 14일 캘린더 카드와 「캘린더 전체보기」가 쓴다.
	 *
	 * <p>금액이 없는 날은 결과에 나오지 않는다 — 화면도 "금액 데이터가 없는 날은 금액 표기 X" 라
	 * 빈 날짜를 서버가 채워 보낼 이유가 없다.
	 */
	@Query("""
			select e.occurredOn, e.type, coalesce(sum(e.amount), 0)
			from Entry e
			where e.groupId = :groupId
			  and e.approvalStatus = com.billage.entry.ApprovalStatus.APPROVED
			  and e.occurredOn between :from and :to
			group by e.occurredOn, e.type
			order by e.occurredOn asc
			""")
	List<Object[]> sumApprovedByDayRaw(@Param("groupId") Long groupId,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** 날짜 → (유형 → 합계). 날짜 순서를 지키려 {@link LinkedHashMap} 을 쓴다. */
	default Map<LocalDate, Map<EntryType, Long>> sumApprovedByDay(Long groupId, LocalDate from, LocalDate to) {
		Map<LocalDate, Map<EntryType, Long>> byDay = new LinkedHashMap<>();
		for (Object[] row : sumApprovedByDayRaw(groupId, from, to)) {
			byDay.computeIfAbsent((LocalDate) row[0], key -> new EnumMap<>(EntryType.class))
					.put((EntryType) row[1], (Long) row[2]);
		}
		return byDay;
	}

	/**
	 * 「통계/분석」의 활성 장부 판정. 최근 며칠 안에 <b>등록된</b> 내역 수를 장부별로 센다.
	 *
	 * <p>발생일이 아니라 생성일 기준이다 — 화면 문구가 "최근 7일간 {N}건의 내역이 추가됨"이라
	 * 과거 날짜로 몰아 넣은 내역도 '요즘 활발한' 신호로 본다.
	 */
	@Query("""
			select e.ledger.id, count(e.id)
			from Entry e
			where e.groupId = :groupId
			  and e.approvalStatus = com.billage.entry.ApprovalStatus.APPROVED
			  and e.createdAt >= :since
			group by e.ledger.id
			order by count(e.id) desc, e.ledger.id asc
			""")
	List<Object[]> countRecentByLedger(@Param("groupId") Long groupId,
			@Param("since") java.time.LocalDateTime since);

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

	/** 기록 보관이 스냅샷을 뜰 때 쓴다. 승인 대기 내역도 함께 담으므로 상태로 거르지 않는다. */
	@Query("select e from Entry e join fetch e.ledger where e.groupId = :groupId order by e.occurredOn asc, e.id asc")
	List<Entry> findAllByGroupId(@Param("groupId") Long groupId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Entry e where e.groupId = :groupId")
	void deleteAllByGroupId(@Param("groupId") Long groupId);
}
