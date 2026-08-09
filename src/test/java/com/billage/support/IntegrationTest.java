package com.billage.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MySQL Testcontainer 위에서 실제 Flyway 마이그레이션·JPA validate를 거치는 통합 테스트 베이스.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTest {

	private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";

	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
			.withDatabaseName("billage")
			.withUsername("billage")
			.withPassword("billage");

	static {
		MYSQL.start();
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("jwt.secret", () -> "test-billage-jwt-secret-key-must-be-at-least-32-bytes-long");
		registry.add("jwt.access-token-validity", () -> "30m");
		registry.add("jwt.refresh-token-validity", () -> "14d");
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * 컨테이너를 전체 테스트가 공유하므로 매 테스트 전에 모든 테이블을 비운다.
	 * <p>
	 * 각 테스트 클래스가 삭제 순서를 직접 관리하면 도메인이 늘 때마다 FK 위반이 생기므로
	 * (예: group_managers가 users를 참조) 여기서 한 번에 정리한다.
	 * JUnit 5는 상위 클래스의 {@code @BeforeEach}를 먼저 실행하므로 하위 클래스 픽스처 생성보다 앞선다.
	 * <p>
	 * 테이블 조회, FK 체크 비활성화, truncate, FK 체크 재활성화를 단일 연결에서 수행하여
	 * 커넥션 풀 환경에서도 일관성을 보장한다.
	 */
	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			List<String> tables = readTableNames(connection);
			try {
				disableForeignKeyChecks(connection);
				truncateTables(connection, tables);
			} finally {
				enableForeignKeyChecks(connection);
			}
			return null;
		});
	}

	private List<String> readTableNames(Connection connection) throws SQLException {
		List<String> tables = new ArrayList<>();
		try (Statement stmt = connection.createStatement();
			 ResultSet rs = stmt.executeQuery(
					 "select table_name from information_schema.tables"
							 + " where table_schema = database() and table_type = 'BASE TABLE'")) {
			while (rs.next()) {
				tables.add(rs.getString(1));
			}
		}
		return tables;
	}

	private void disableForeignKeyChecks(Connection connection) throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("set foreign_key_checks = 0");
		}
	}

	private void truncateTables(Connection connection, List<String> tables) throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			for (String table : tables) {
				if (!FLYWAY_HISTORY_TABLE.equals(table)) {
					stmt.execute("truncate table `" + table + "`");
				}
			}
		}
	}

	private void enableForeignKeyChecks(Connection connection) throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("set foreign_key_checks = 1");
		}
	}
}
