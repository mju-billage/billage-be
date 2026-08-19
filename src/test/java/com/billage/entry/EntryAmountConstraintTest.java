package com.billage.entry;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.billage.support.IntegrationTest;

/**
 * 금액 제약(V8)이 DB 에서도 걸리는지 확인한다.
 * 애플리케이션 검증을 우회한 경로로도 음수·0·상한 초과 금액이 들어가면 안 된다.
 */
class EntryAmountConstraintTest extends IntegrationTest {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 애플리케이션을_우회해도_잘못된_금액은_저장되지_않는다() {
		assertThatThrownBy(() -> insertWithAmount(0L)).isInstanceOf(Exception.class);
		assertThatThrownBy(() -> insertWithAmount(-1L)).isInstanceOf(Exception.class);
		assertThatThrownBy(() -> insertWithAmount(1_000_000_000L)).isInstanceOf(Exception.class);
	}

	private void insertWithAmount(long amount) {
		jdbcTemplate.update("""
				insert into entry (group_id, ledger_id, type, title, amount, occurred_on,
					approval_status, created_by_user_id, created_by_name, created_at, updated_at, version)
				values (1, 1, 'EXPENSE', '제약 테스트', ?, '2026-07-20', 'APPROVED', 1, '총무', now(6), now(6), 0)
				""", amount);
	}
}
