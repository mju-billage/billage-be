package com.billage.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

	@Autowired
	private DatabaseCleaner databaseCleaner;

	/** 테스트 클래스 간 데이터가 섞이지 않도록 각 테스트 전에 전체 테이블을 비운다. */
	@BeforeEach
	void clearDatabase() {
		databaseCleaner.clear();
	}

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
}
