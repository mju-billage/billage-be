package com.billage.membership;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

	Optional<GroupInvitation> findByCode(String code);

	boolean existsByCode(String code);

	void deleteByGroupId(Long groupId);
}
