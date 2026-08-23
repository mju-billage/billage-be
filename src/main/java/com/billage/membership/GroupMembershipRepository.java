package com.billage.membership;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.billage.group.dto.GroupListRow;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {

	Optional<GroupMembership> findByGroupIdAndUserId(Long groupId, Long userId);

	Optional<GroupMembership> findByIdAndGroupId(Long id, Long groupId);

	boolean existsByGroupIdAndUserId(Long groupId, Long userId);

	long countByGroupIdAndRole(Long groupId, GroupRole role);

	/** 관리자 목록. role 은 문자열로 저장돼 정렬 순서가 뒤집히므로 총무를 앞으로 명시한다. */
	@Query("""
			select ms from GroupMembership ms
			where ms.group.id = :groupId
			order by case when ms.role = com.billage.membership.GroupRole.OWNER then 0 else 1 end,
			         ms.joinedAt asc
			""")
	List<GroupMembership> findByGroupId(@Param("groupId") Long groupId);

	void deleteByGroupId(Long groupId);

	/**
	 * 총무 행을 잠근 채로 가져온다. 총무 수를 줄이기 직전에 쓴다.
	 *
	 * <p>단순 count 로는 막을 수 없다 — MySQL REPEATABLE READ 에서 일반 조회는 트랜잭션 시작 시점
	 * 스냅샷을 보므로, 공동 총무 둘이 서로를 동시에 내보내면 양쪽 다 "총무 2명"을 읽고 커밋해
	 * 총무가 0명인 모임이 남는다(복구 경로가 없다). 잠금 조회는 최신 커밋 값을 읽고,
	 * 같은 모임의 총무 변경 작업을 직렬화한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select ms from GroupMembership ms
			where ms.group.id = :groupId and ms.role = com.billage.membership.GroupRole.OWNER
			""")
	List<GroupMembership> lockOwners(@Param("groupId") Long groupId);

	/**
	 * 내 모임 목록. `memberCount` 는 납부 관리용 명단(Member) 수이며, 상관 서브쿼리로 함께 조회해 N+1 을 피한다.
	 */
	@Query("""
			select new com.billage.group.dto.GroupListRow(
				g.id, g.name, g.description, ms.role,
				(select count(m.id) from Member m where m.group = g),
				g.createdAt)
			from GroupMembership ms
			join ms.group g
			where ms.userId = :userId
			order by g.createdAt desc
			""")
	List<GroupListRow> findMyGroups(@Param("userId") Long userId);
}
