package com.billage.helpcenter;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 약관 전문. 종류별 한 행이며 원문을 받기 전에는 행이 없다. Flyway 로만 넣고 고친다. */
@Entity
@Table(name = "terms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "term_type", length = 20)
	private TermType termType;

	@Column(nullable = false, columnDefinition = "MEDIUMTEXT")
	private String body;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}
