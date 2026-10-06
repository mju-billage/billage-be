package com.billage.auth.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class TemporaryPasswordGeneratorTest {

	/** 가입 요청({@code SignupRequest})의 비밀번호 규칙과 같은 식이다. */
	private static final Pattern SIGNUP_RULE =
			Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

	private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

	@Test
	void 항상_가입_비밀번호_규칙을_만족한다() {
		for (int i = 0; i < 1_000; i++) {
			String password = generator.generate();

			assertThat(password).hasSize(TemporaryPasswordGenerator.LENGTH);
			assertThat(SIGNUP_RULE.matcher(password).matches()).as(password).isTrue();
		}
	}

	@Test
	void 헷갈리는_글자를_쓰지_않는다() {
		for (int i = 0; i < 1_000; i++) {
			assertThat(generator.generate()).doesNotContainPattern("[0O1lI]");
		}
	}

	@Test
	void 매번_다른_값을_만든다() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 1_000; i++) {
			seen.add(generator.generate());
		}

		assertThat(seen).hasSize(1_000);
	}
}
