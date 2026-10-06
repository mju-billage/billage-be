package com.billage.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스 가입 계정. 이메일/비밀번호 또는 소셜 로그인(구글·카카오)으로 생성된다.
 * 비밀번호는 BCrypt 해시만 저장하며, 소셜 전용 계정은 비밀번호가 없다.
 * 인증 도메인이 다른 도메인에 강하게 결합되지 않도록 최소 필드만 둔다.
 *
 * <p>{@code @DynamicUpdate}: 바뀐 컬럼만 UPDATE 한다. 기본 동작은 모든 컬럼을 다시 쓰기 때문에,
 * 이름만 고치는 요청이 그 사이에 끝난 비밀번호 재설정을 읽어 둔 옛 값으로 덮어써 되돌린다 —
 * 사용자는 메일로 받은 임시 비밀번호로 로그인하지 못하게 된다.
 */
@Entity
@DynamicUpdate
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	/** 소셜 전용 계정은 비밀번호가 없다({@link #createSocial}). */
	private String password;

	@Column(nullable = false, length = 10)
	private String name;

	@Column(name = "terms_agreed_at")
	private LocalDateTime termsAgreedAt;

	/** 선택 항목인 마케팅 수신 동의 시각. 동의하지 않았으면 null 이다. */
	@Column(name = "marketing_agreed_at")
	private LocalDateTime marketingAgreedAt;

	/** 비밀번호 재설정 발송 횟수. {@link #passwordResetWindowStartedAt} 부터 센 값이다. */
	@Column(name = "password_reset_count", nullable = false)
	private int passwordResetCount;

	/** 재설정 발송 횟수를 세기 시작한 시각. 한 번도 재설정하지 않았으면 null 이다. */
	@Column(name = "password_reset_window_started_at")
	private LocalDateTime passwordResetWindowStartedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private User(String email, String password, String name, LocalDateTime termsAgreedAt) {
		this.email = email;
		this.password = password;
		this.name = name;
		this.termsAgreedAt = termsAgreedAt;
	}

	/**
	 * @param password 반드시 BCrypt로 인코딩된 값
	 */
	public static User create(String email, String password, String name) {
		return new User(email, password, name, null);
	}

	/** 소셜 로그인(구글·카카오) 최초 가입. 비밀번호 없이 생성되며, 약관 동의 시각을 함께 기록한다. */
	public static User createSocial(String email, String name, LocalDateTime termsAgreedAt) {
		return new User(email, null, name, termsAgreedAt);
	}

	/**
	 * 약관 동의 시각을 기록한다. 이미 동의 기록이 있으면 유지한다
	 * (예: 이메일로 먼저 가입한 계정에 소셜 계정을 처음 연결하는 시점에 최초 기록됨).
	 */
	public void agreeToTermsIfNeeded(LocalDateTime at) {
		if (this.termsAgreedAt == null) {
			this.termsAgreedAt = at;
		}
	}

	/** 내 프로필 수정. 이름만 바꾼다 — 이메일 변경은 MVP 범위 밖이다. */
	public void changeName(String name) {
		this.name = name;
	}

	/**
	 * @param encodedPassword 반드시 BCrypt로 인코딩된 값
	 */
	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
	}

	/** 소셜 전용 계정에는 앱 비밀번호가 없다. 비밀번호 변경·재설정은 이 계정에 적용할 수 없다. */
	public boolean hasPassword() {
		return this.password != null;
	}

	/** 창 안에서 이미 상한만큼 임시 비밀번호를 보냈는지. */
	public boolean passwordResetLimitExceeded(LocalDateTime now, int windowMinutes, int maxSends) {
		return passwordResetWindowAlive(now, windowMinutes) && passwordResetCount >= maxSends;
	}

	/**
	 * 임시 비밀번호로 바꾸고 발송 횟수를 센다. 창이 지났으면 새로 센다.
	 *
	 * @param encodedPassword 반드시 BCrypt로 인코딩된 값
	 */
	public void resetPassword(String encodedPassword, LocalDateTime now, int windowMinutes) {
		if (!passwordResetWindowAlive(now, windowMinutes)) {
			this.passwordResetWindowStartedAt = now;
			this.passwordResetCount = 0;
		}
		this.passwordResetCount += 1;
		this.password = encodedPassword;
	}

	private boolean passwordResetWindowAlive(LocalDateTime now, int windowMinutes) {
		return passwordResetWindowStartedAt != null
				&& !passwordResetWindowStartedAt.plusMinutes(windowMinutes).isBefore(now);
	}

	/** 가입 시 받은 마케팅 수신 동의. 동의한 경우에만 시각을 남긴다. */
	public void recordMarketingAgreement(boolean agreed, LocalDateTime at) {
		this.marketingAgreedAt = agreed ? at : null;
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
