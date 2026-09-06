package com.billage.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

	/**
	 * 계정 행을 잠그고 가져온다. 탈퇴가 쓴다.
	 *
	 * <p>탈퇴는 그 사람이 올린 파일을 정리한 뒤 계정을 지운다. 잠그지 않으면 그 사이에 들어온 업로드가
	 * 정리 대상에서 빠진 채 남아, 뒤이어 업로더 표시만 비워져 아무도 열지도 지우지도 못하는 고아가 된다.
	 * 계정 행을 잠가 두면 새 업로드의 FK 검사가 여기서 기다렸다가 탈퇴가 끝난 뒤 실패한다 —
	 * 그 요청은 저장소에 바이트를 쓰기 전에 되돌아가므로 아무것도 남기지 않는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :userId")
	Optional<User> findByIdForUpdate(@Param("userId") Long userId);

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
