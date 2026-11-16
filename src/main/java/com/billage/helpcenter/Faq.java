package com.billage.helpcenter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 자주 묻는 질문. 내용은 Flyway 마이그레이션으로만 넣고 고친다(백오피스 없음). */
@Entity
@Table(name = "faq")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String question;

	/** 줄바꿈은 {@code \n}, 빈 줄은 {@code \n\n}. 앱이 그대로 보여 준다. */
	@Column(nullable = false, columnDefinition = "TEXT")
	private String answer;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;
}
