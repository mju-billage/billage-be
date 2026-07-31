package com.billage.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.billage.auth.dto.LoginResponse;
import com.billage.auth.token.RefreshTokenRepository;
import com.billage.common.exception.BusinessException;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

class RefreshConcurrencyTest extends IntegrationTest {

	@Autowired
	AuthService authService;
	@Autowired
	UserRepository userRepository;
	@Autowired
	RefreshTokenRepository refreshTokenRepository;
	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
		userRepository.save(User.create("member@example.com", passwordEncoder.encode("password123!"), "홍길동"));
	}

	@Test
	void 동시_재발급시_하나만_성공() throws Exception {
		LoginResponse login = authService.login("member@example.com", "password123!");
		String refreshToken = login.refreshToken();

		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			List<Callable<Boolean>> tasks = java.util.Collections.nCopies(threads, () -> {
				try {
					authService.refresh(refreshToken);
					return true; // 성공
				} catch (BusinessException e) {
					return false; // 재사용/경합으로 실패
				}
			});

			List<Future<Boolean>> results = executor.invokeAll(tasks);
			long successCount = 0;
			for (Future<Boolean> result : results) {
				if (result.get()) {
					successCount++;
				}
			}

			// 같은 Refresh Token 으로 동시에 재발급을 시도해도 정확히 하나만 성공
			assertThat(successCount).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}
}
