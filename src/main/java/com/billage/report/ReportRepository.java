package com.billage.report;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

	/**
	 * 목록은 report 테이블의 스냅샷 집계값만 쓰므로 자식 테이블을 읽지 않는다.
	 *
	 * <p>{@code reportType} 은 화면의 장부별/기간별 탭이며 null 이면 조건에서 빠진다.
	 */
	@Query("""
			select r from Report r
			where r.groupId = :groupId
			  and (:reportType is null or r.reportType = :reportType)
			""")
	Page<Report> search(@Param("groupId") Long groupId, @Param("reportType") ReportType reportType,
			Pageable pageable);

	/** 모임 삭제용. 자식 스냅샷은 cascade + orphanRemoval 로 함께 지워진다. */
	List<Report> findAllByGroupId(Long groupId);
}
