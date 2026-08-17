package com.billage.report;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

	/** 목록은 report 테이블의 스냅샷 집계값만 쓰므로 자식 테이블을 읽지 않는다. */
	Page<Report> findAllByGroupId(Long groupId, Pageable pageable);

	/** 모임 삭제용. 자식 스냅샷은 cascade + orphanRemoval 로 함께 지워진다. */
	List<Report> findAllByGroupId(Long groupId);
}
