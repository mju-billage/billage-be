package com.billage.report;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportLedgerRepository extends JpaRepository<ReportLedger, Long> {

	/**
	 * 보고서 상세용 장부 스냅샷. 내역까지 fetch join 해 장부 수만큼 쿼리가 나가는 것을 막는다.
	 * (Report → ReportLedger → ReportEntry 를 한 번에 fetch 하면 컬렉션이 둘이라 실행할 수 없다.)
	 */
	@Query("""
			select distinct rl from ReportLedger rl
			left join fetch rl.entries
			where rl.report.id = :reportId
			order by rl.id asc
			""")
	List<ReportLedger> findAllByReportIdWithEntries(@Param("reportId") Long reportId);
}
