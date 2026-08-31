package com.billage.dues;

import java.time.LocalDateTime;
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

	/**
	 * 한 모임원의 납부 완료 기록. 「모임원 상세 > 납부 내역」 화면이 쓴다.
	 *
	 * <p>회비명·장부와 함께 보여 주므로 fetch join 으로 회비를 같이 읽는다.
	 */
	@Query("""
			select dm from DuesMember dm
			join fetch dm.dues d
			where dm.member.id = :memberId
			  and dm.status = com.billage.dues.PaymentStatus.PAID
			  and (:from is null or dm.paidAt >= :from)
			  and (:to is null or dm.paidAt < :to)
			order by dm.paidAt desc, dm.id desc
			""")
	List<DuesMember> findPaymentsOf(@Param("memberId") Long memberId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	/** 여러 모임원의 총 납부 금액. 모임원 목록에서 N+1 없이 채우려고 한 번에 센다. */
	@Query("""
			select dm.member.id, coalesce(sum(d.amount), 0)
			from DuesMember dm
			join dm.dues d
			where dm.member.id in :memberIds and dm.status = com.billage.dues.PaymentStatus.PAID
			group by dm.member.id
			""")
	List<Object[]> sumPaidByMemberRaw(@Param("memberIds") Collection<Long> memberIds);

	default Map<Long, Long> sumPaidByMember(Collection<Long> memberIds) {
		if (memberIds.isEmpty()) {
			return Map.of();
		}
		return sumPaidByMemberRaw(memberIds).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
	}

	/** 모임원을 명단에서 지울 때 그 사람의 회비 참여 데이터도 함께 지운다(기획 확정). */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from DuesMember dm where dm.member.id = :memberId")
	void deleteAllByMemberId(@Param("memberId") Long memberId);
}
