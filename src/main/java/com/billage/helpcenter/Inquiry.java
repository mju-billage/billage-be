package com.billage.helpcenter;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1:1 문의. 회원이 탈퇴하면 DB 의 {@code ON DELETE SET NULL} 로 작성자 연결만 끊기고 문의는 남는다.
 * 답장은 앱이 아니라 받은 메일 주소로 보낸다.
 */
@Entity
@Table(name = "inquiry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry {

	public static final int MAX_TITLE_LENGTH = 50;
	public static final int MAX_CONTENT_LENGTH = 1000;
	/** 한 사람이 24시간 안에 보낼 수 있는 문의 수. 알림 메일이 인증 메일과 같은 발송 한도를 쓰기 때문이다. */
	public static final int DAILY_LIMIT_PER_USER = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id")
	private Long userId;

	@Column(nullable = false, length = 255)
	private String email;

	@Column(nullable = false, length = MAX_TITLE_LENGTH)
	private String title;

	@Column(nullable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private Inquiry(Long userId, String email, String title, String content) {
		this.userId = userId;
		this.email = email;
		this.title = title;
		this.content = content;
	}

	public static Inquiry create(Long userId, String email, String title, String content) {
		return new Inquiry(userId, email, title, content);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
