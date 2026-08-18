package com.billage.member;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByIdAndGroupId(Long id, Long groupId);

	long countByGroupId(Long groupId);

	void deleteByGroupId(Long groupId);

	/**
	 * 모임원 명단. 이름 부분 일치 검색은 선택값이며 null 이면 조건에서 제외된다.
	 */
	@Query("""
			select m from Member m
			where m.group.id = :groupId
			  and (:keyword is null or m.name like concat('%', :keyword, '%'))
			order by m.name asc
			""")
	List<Member> search(@Param("groupId") Long groupId, @Param("keyword") String keyword);
}
