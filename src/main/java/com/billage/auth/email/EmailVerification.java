package com.billage.auth.email;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원가입 이메일 인증 상태. 이메일당 한 행이며 발송할 때마다 코드가 바뀐다.
 *
 * <p>코드는 해시로만 저장한다 — 짧고 수명이 짧아도 평문으로 두면 DB 를 읽는 사람이 남의 가입을 가로챌 수 있다.
 */
@Entity
@Table(name = "email_verification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255, updatable = false)
	private String email;

	@Column(name = "code_hash", nullable = false, length = 255)
	private String codeHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "send_count", nullable = false)
	private int sendCount;

	@Column(name = "send_window_started_at", nullable = false)
	private LocalDateTime sendWindowStartedAt;

	@Column(name = "verified_at")
	private LocalDateTime verifiedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private EmailVerification(String email, String codeHash, LocalDateTime expiresAt, LocalDateTime now) {
		this.email = email;
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
		this.sendCount = 1;
		this.sendWindowStartedAt = now;
	}

	public static EmailVerification issue(String email, String codeHash, LocalDateTime expiresAt,
			LocalDateTime now) {
		return new EmailVerification(email, codeHash, expiresAt, now);
	}

	/**
	 * 재전송. 코드와 만료를 새로 쓰고 시도 횟수를 되돌린다 — 화면의 타이머도 3분으로 리셋된다.
	 * 이전 코드는 이 시점에 무효가 된다.
	 */
	public void reissue(String codeHash, LocalDateTime expiresAt, LocalDateTime now, int sendWindowMinutes) {
		if (sendWindowStartedAt.plusMinutes(sendWindowMinutes).isBefore(now)) {
			this.sendWindowStartedAt = now;
			this.sendCount = 0;
		}
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
		this.attemptCount = 0;
		this.sendCount += 1;
		this.verifiedAt = null;
	}

	public boolean isExpired(LocalDateTime at) {
		return expiresAt.isBefore(at);
	}

	public boolean isVerified() {
		return verifiedAt != null;
	}

	public boolean sendLimitExceeded(LocalDateTime now, int sendWindowMinutes, int maxSends) {
		boolean windowAlive = !sendWindowStartedAt.plusMinutes(sendWindowMinutes).isBefore(now);
		return windowAlive && sendCount >= maxSends;
	}

	public void addAttempt() {
		this.attemptCount += 1;
	}

	public boolean attemptLimitExceeded(int maxAttempts) {
		return attemptCount >= maxAttempts;
	}

	public void markVerified(LocalDateTime at) {
		this.verifiedAt = at;
	}

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
