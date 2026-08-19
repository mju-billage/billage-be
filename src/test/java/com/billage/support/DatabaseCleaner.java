package com.billage.support;

import java.sql.Statement;
import java.util.List;

import org.springframework.jdbc.core.ConnectionCallback;
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

		// execute() 는 호출마다 커넥션을 새로 얻으므로, 풀이 다른 커넥션을 주면
		// FOREIGN_KEY_CHECKS = 0 이 TRUNCATE 에 적용되지 않는다. 한 커넥션에서 처리하고 반드시 복구한다.
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			try (Statement statement = connection.createStatement()) {
				statement.execute("SET FOREIGN_KEY_CHECKS = 0");
				try {
					for (String table : tables) {
						statement.execute("TRUNCATE TABLE `" + table + "`");
					}
				} finally {
					statement.execute("SET FOREIGN_KEY_CHECKS = 1");
				}
			}
			return null;
		});
	}
}
