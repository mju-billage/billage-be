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

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "terms_agreed_at")
	private LocalDateTime termsAgreedAt;

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
