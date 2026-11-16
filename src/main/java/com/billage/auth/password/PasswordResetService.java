package com.billage.auth.password;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.auth.token.RefreshTokenRepository;
import com.billage.common.mail.MailSender;
import com.billage.user.User;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 비밀번호 재설정(화면 COM-2-PAGE-02-0). 재설정 링크가 아니라 임시 비밀번호를 메일로 보내고
 * 기존 비밀번호를 그것으로 바꾼다.
 *
 * <p>로그인 없이 이메일만으로 부르는 API 라, 응답으로 가입 여부를 알려 주지 않는다 —
 * 가입되지 않은 주소, 소셜 전용 계정, 발송 횟수를 넘긴 계정 모두 아무 일도 하지 않고 조용히 끝난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final TemporaryPasswordGenerator generator;
	private final MailSender mailSender;
	private final PasswordResetProperties properties;

	/**
	 * 메일은 트랜잭션 안에서 보낸다. 발송이 실패하면 비밀번호 변경까지 되돌아가야 한다 —
	 * 먼저 커밋하고 발송에 실패하면, 사용자는 옛 비밀번호도 새 비밀번호도 모르는 채로 잠긴다.
	 */
	@Transactional
	public void reset(String rawEmail) {
		Optional<User> found = userRepository.findByEmailForUpdate(rawEmail.trim());
		if (found.isEmpty()) {
			return;
		}
		User user = found.get();
		if (!user.hasPassword()) {
			// 소셜 전용 계정에는 앱 비밀번호가 없다. 여기서 만들어 주면 소셜 로그인 말고 다른 입구가 생긴다.
			return;
		}
		LocalDateTime now = LocalDateTime.now();
		if (user.passwordResetLimitExceeded(now, properties.sendWindowMinutes(), properties.maxSends())) {
			log.warn("비밀번호 재설정 발송 횟수 초과. userId={}", user.getId());
			return;
		}

		String temporaryPassword = generator.generate();
		user.resetPassword(passwordEncoder.encode(temporaryPassword), now, properties.sendWindowMinutes());
		// 누가 계정을 쓰고 있었든 전부 끊는다. 비밀번호를 잊은 사람은 어느 기기에도 로그인해 있지 않다고 본다.
		refreshTokenRepository.revokeActiveForUser(user.getId(), null, now);

		mailSender.send(user.getEmail(), "[빌리지] 임시 비밀번호 안내", body(temporaryPassword));
	}

	private String body(String temporaryPassword) {
		return """
				임시 비밀번호는 %s 입니다.
				이 비밀번호로 로그인한 뒤 「더보기 > 설정 > 내 프로필 > 비밀번호 변경」에서 새 비밀번호로 바꿔 주세요.

				기존 비밀번호는 더 이상 쓸 수 없고, 로그인해 있던 기기는 모두 로그아웃됩니다.
				""".formatted(temporaryPassword);
	}
}
