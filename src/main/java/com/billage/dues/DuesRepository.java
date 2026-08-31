package com.billage.dues;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DuesRepository extends JpaRepository<Dues, Long> {

	/**
	 * 회비 목록.
	 *
	 * <p>화면의 상태 탭은 '납부 예정'까지 셋인데 DB 의 {@code status} 는 OPEN|CLOSED 둘뿐이라,
	 * 서비스가 탭을 <b>저장 상태({@code status})</b>와 <b>시작 여부({@code started})</b> 두 조건으로 쪼개 넘긴다.
	 * <ul>
	 *   <li>납부 예정 → {@code status=OPEN, started=false}</li>
	 *   <li>진행 중 → {@code status=OPEN, started=true}</li>
	 *   <li>마감 → {@code status=CLOSED, started=null}</li>
	 *   <li>전체 → 둘 다 {@code null}</li>
	 * </ul>
	 *
	 * <p>정렬은 화면명세가 지정한 '예정 → 진행 중 → 마감' 묶음 순서로 고정한다. 예정은 시작일이 빠른 순,
	 * 진행 중은 마감이 임박한 순이라 그룹마다 정렬 기준이 다르다 — 클라이언트가 페이지 단위로
	 * 다시 정렬할 수 없으므로 서버가 정한다.
	 */
	@Query("""
			select d from Dues d
			where d.groupId = :groupId
			  and (:status is null or d.status = :status)
			  and (:started is null
			       or (:started = true and d.startDate <= :today)
			       or (:started = false and d.startDate > :today))
			order by
			  case when d.status = com.billage.dues.DuesStatus.CLOSED then 2
			       when d.startDate > :today then 0
			       else 1 end asc,
			  case when d.startDate > :today then d.startDate else d.dueDate end asc,
			  d.id desc
			""")
	Page<Dues> search(@Param("groupId") Long groupId, @Param("status") DuesStatus status,
			@Param("started") Boolean started, @Param("today") LocalDate today, Pageable pageable);

	/**
	 * 대시보드의 '회비 현황 카드'. 마감이 임박한 순으로 몇 건만 뽑는다.
	 *
	 * <p>마감된 회비는 뺀다 — 이미 장부 내역으로 반영돼 있고 화면도 진행 중인 것만 보여 준다.
	 * 아직 시작 전(SCHEDULED)인 회비는 저장 상태가 OPEN 이라 함께 나온다. 화면이 D-day 뱃지로
	 * 구분해 주므로 서버가 걸러 내지 않는다.
	 */
	@Query("""
			select d from Dues d
			where d.groupId = :groupId and d.status = com.billage.dues.DuesStatus.OPEN
			order by d.dueDate asc, d.id asc
			""")
	List<Dues> findUpcoming(@Param("groupId") Long groupId, Pageable pageable);

	/** 대시보드용. 진행 중인 회비 수. */
	long countByGroupIdAndStatus(Long groupId, DuesStatus status);

	/** 모임 삭제용. 대상자(dues_member)는 cascade + orphanRemoval 로 함께 지워진다. */
	List<Dues> findAllByGroupId(Long groupId);
}
