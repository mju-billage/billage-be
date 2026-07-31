package com.billage.auth.token;

import java.time.LocalDateTime;

import com.billage.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Refresh Token. 원문은 저장하지 않고 SHA-256 해시({@code tokenHash})만 보관한다.
 * <p>
 * {@code familyId}는 하나의 로그인 세션에서 회전(Rotation)되는 토큰들을 묶는 식별자다.
 * 이미 회전된 토큰이 다시 제출되면 같은 {@code familyId}의 활성 토큰을 전부 폐기해 재사용을 차단한다.
 * 상태 전이는 setter가 아닌 도메인 메서드로만 수행한다.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "family_id", nullable = false, length = 36)
	private String familyId;

	@Column(name = "device_id")
	private String deviceId;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "last_used_at")
	private LocalDateTime lastUsedAt;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	@Column(name = "replaced_by_token_id")
	private Long replacedByTokenId;

	@Enumerated(EnumType.STRING)
	@Column(name = "revoke_reason", length = 20)
	private RevokeReason revokeReason;

	private RefreshToken(User user, String tokenHash, String familyId, String deviceId, LocalDateTime expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.familyId = familyId;
		this.deviceId = deviceId;
		this.expiresAt = expiresAt;
	}

	/** 새 로그인 세션의 첫 토큰. 새 familyId를 부여한다. */
	public static RefreshToken issue(User user, String tokenHash, String familyId, String deviceId,
			LocalDateTime expiresAt) {
		return new RefreshToken(user, tokenHash, familyId, deviceId, expiresAt);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	// --- 상태 조회 ---

	/** 만료 여부. */
	public boolean isExpired(LocalDateTime now) {
		return !expiresAt.isAfter(now);
	}

	/** 폐기 여부. */
	public boolean isRevoked() {
		return revokedAt != null;
	}

	/** 재발급에 사용 가능한 유효 상태인지. */
	public boolean isActive(LocalDateTime now) {
		return !isRevoked() && !isExpired(now);
	}

	// --- 상태 전이 ---

	/**
	 * 회전 처리: 이 토큰을 폐기하고 후속 토큰을 가리킨다.
	 *
	 * @param replacementTokenId 저장이 끝나 id가 확정된 새 토큰의 id
	 */
	public void rotate(Long replacementTokenId, LocalDateTime now) {
		markRevoked(RevokeReason.ROTATED, now);
		this.replacedByTokenId = replacementTokenId;
	}

	/** 로그아웃 폐기. 이미 폐기된 토큰이면 멱등하게 무시한다. */
	public void revokeForLogout(LocalDateTime now) {
		if (isRevoked()) {
			return;
		}
		markRevoked(RevokeReason.LOGOUT, now);
	}

	/** 재발급에 성공적으로 사용된 시각 기록. */
	public void markUsed(LocalDateTime now) {
		this.lastUsedAt = now;
	}

	private void markRevoked(RevokeReason reason, LocalDateTime now) {
		this.revokedAt = now;
		this.revokeReason = reason;
	}
}
