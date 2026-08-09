package com.billage.group;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, Long> {

	boolean existsByInviteCode(String inviteCode);
}
