package com.billage.helpcenter;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공지사항. 내용은 Flyway 마이그레이션으로만 넣고 고친다(백오피스 없음). */
@Entity
@Table(name = "notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
