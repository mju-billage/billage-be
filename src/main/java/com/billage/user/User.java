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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스 가입 계정. 이메일/비밀번호 또는 소셜 로그인(구글·카카오)으로 생성된다.
 * 비밀번호는 BCrypt 해시만 저장하며, 소셜 전용 계정은 비밀번호가 없다.
 * 인증 도메인이 다른 도메인에 강하게 결합되지 않도록 최소 필드만 둔다.
 */
@Entity
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
