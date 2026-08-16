package com.billage.support;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 테스트 간 데이터 격리. 테이블마다 삭제 순서를 맞추다 보면 FK 제약에 계속 걸리므로
 * FK 검사를 잠시 끄고 전체 테이블을 비운다(Flyway 이력은 제외).
 */
@Component
@RequiredArgsConstructor
public class DatabaseCleaner {

	private final JdbcTemplate jdbcTemplate;

	public void clear() {
		List<String> tables = jdbcTemplate.queryForList("""
				select table_name from information_schema.tables
				where table_schema = database()
				  and table_type = 'BASE TABLE'
				  and table_name <> 'flyway_schema_history'
				""", String.class);

		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
		tables.forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE `" + table + "`"));
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
	}
}
