package com.billage.group;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupSpaceRepository extends JpaRepository<GroupSpace, Long> {

	/** 대표 이미지를 쓰고 있는 모임. 파일 열람 권한을 판정할 때 쓴다. */
	Optional<GroupSpace> findByGroupImageFileId(Long groupImageFileId);
}
