package com.billage.membership;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
	 * 내 모임 목록. `memberCount` 는 납부 관리용 명단(Member) 수이며, 상관 서브쿼리로 함께 조회해 N+1 을 피한다.
	 */
	@Query("""
			select new com.billage.group.dto.GroupListRow(
				g.id, g.name, g.description, g.groupImageFileId, ms.role,
				(select count(m.id) from Member m where m.group = g),
				g.createdAt)
			from GroupMembership ms
			join ms.group g
			where ms.userId = :userId
			order by g.createdAt desc
			""")
	List<GroupListRow> findMyGroups(@Param("userId") Long userId);
}
