package com.billage.file;

import java.time.LocalDateTime;

import com.billage.entry.Entry;

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
 * 업로드된 파일의 메타데이터. 바이너리는 {@link FileStorage} 가 보관하고 DB에는 storage key 만 둔다.
 * 엔티티명이 {@code File} 이면 {@link java.io.File} 과 헷갈리므로 UploadedFile 로 둔다(테이블은 {@code file}).
 */
@Entity
@Table(name = "file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadedFile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FilePurpose purpose;

	/** 저장소 내 경로. 원본 파일명을 그대로 쓰지 않는다. */
	@Column(name = "storage_key", nullable = false, length = 255, updatable = false)
	private String storageKey;

	@Column(name = "original_file_name", nullable = false, length = 255)
	private String originalFileName;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(nullable = false)
	private Long size;

	@Column(name = "uploaded_by", nullable = false, updatable = false)
	private Long uploadedBy;

	/** 증빙이 내역에 연결되면 값이 채워진다. 연결된 파일은 삭제할 수 없다. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "entry_id")
	private Entry entry;

	/**
	 * 대표 이미지로 쓰이는 모임. 증빙이 {@code entry} 로 주인을 적는 것과 같은 자리다.
	 * 값이 있으면 그 모임의 대표 이미지이며, 모임당 하나만 가질 수 있다(uk_file_group).
	 */
	@Column(name = "group_id")
	private Long groupId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private UploadedFile(FilePurpose purpose, String storageKey, String originalFileName, String contentType,
			Long size, Long uploadedBy) {
		this.purpose = purpose;
		this.storageKey = storageKey;
		this.originalFileName = originalFileName;
		this.contentType = contentType;
		this.size = size;
		this.uploadedBy = uploadedBy;
	}

	public static UploadedFile create(FilePurpose purpose, String storageKey, String originalFileName,
			String contentType, Long size, Long uploadedBy) {
		return new UploadedFile(purpose, storageKey, originalFileName, contentType, size, uploadedBy);
	}

	/** 어딘가에 쓰이고 있는 파일. 지우려면 먼저 연결을 끊어야 한다. */
	public boolean isLinked() {
		return this.entry != null || this.groupId != null;
	}

	public void linkTo(Entry entry) {
		this.entry = entry;
	}

	public boolean isUploadedBy(Long userId) {
		return this.uploadedBy.equals(userId);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
