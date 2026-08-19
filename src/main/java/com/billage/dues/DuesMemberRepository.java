package com.billage.dues;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DuesMemberRepository extends JpaRepository<DuesMember, Long> {

	/** 회비 목록의 납부/전체 인원을 N+1 없이 한 번에 센다. */
	@Query("""
			select dm.dues.id, dm.status, count(dm.id)
			from DuesMember dm
			where dm.dues.id in :duesIds
			group by dm.dues.id, dm.status
			""")
	List<Object[]> countByDuesRaw(@Param("duesIds") Collection<Long> duesIds);

	default Map<Long, Map<PaymentStatus, Long>> countByDues(Collection<Long> duesIds) {
		if (duesIds.isEmpty()) {
			return Map.of();
		}
		return countByDuesRaw(duesIds).stream().collect(Collectors.groupingBy(
				row -> (Long) row[0],
				Collectors.toMap(row -> (PaymentStatus) row[1], row -> (Long) row[2])));
	}

	/** 납부 대상 목록. 이름을 함께 쓰므로 fetch join 으로 N+1 을 피한다. */
	@Query("""
			select dm from DuesMember dm
			join fetch dm.member m
			where dm.dues.id = :duesId
			  and (:status is null or dm.status = :status)
			  and (:keyword is null or m.name like concat('%', :keyword, '%'))
			order by m.name asc, dm.id asc
			""")
	List<DuesMember> findTargets(@Param("duesId") Long duesId,
			@Param("status") PaymentStatus status,
			@Param("keyword") String keyword);

	/** 대시보드용. 진행 중(OPEN) 회비의 납부 상태별 인원을 모임 단위로 센다. */
	@Query("""
			select dm.status, count(dm.id)
			from DuesMember dm
			where dm.dues.groupId = :groupId and dm.dues.status = com.billage.dues.DuesStatus.OPEN
			group by dm.status
			""")
	List<Object[]> countOpenTargetsByGroupRaw(@Param("groupId") Long groupId);

	default Map<PaymentStatus, Long> countOpenTargetsByGroup(Long groupId) {
		return countOpenTargetsByGroupRaw(groupId).stream()
				.collect(Collectors.toMap(row -> (PaymentStatus) row[0], row -> (Long) row[1]));
	}

	/** 모임원을 명단에서 지울 때 그 사람의 회비 참여 데이터도 함께 지운다(기획 확정). */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from DuesMember dm where dm.member.id = :memberId")
	void deleteAllByMemberId(@Param("memberId") Long memberId);
}
