package com.billage.ledger;

import java.time.LocalDateTime;

import com.billage.folder.Folder;
import com.billage.group.GroupSpace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수입·지출 내역이 쌓이는 장부. 폴더에 속하며 같은 모임 안에서만 폴더를 옮길 수 있다.
 * {@code budget} 은 월별이 아니라 장부 전체 기간에 적용되는 총예산이며 자동 초기화되지 않는다.
 */
@Entity
@Table(name = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ledger {

	/** 기획 공통 정책상 금액 상한. */
	public static final long MAX_BUDGET = 999_999_999L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private GroupSpace group;

	/** null 이면 폴더에 속하지 않은 최상위 영역 장부(상위 폴더가 삭제된 경우). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "folder_id")
	private Folder folder;

	@Column(nullable = false, length = 20)
	private String name;

	/** 미설정 가능. */
	private Long budget;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Version
	private Long version;

	private Ledger(GroupSpace group, Folder folder, String name, Long budget) {
		this.group = group;
		this.folder = folder;
		this.name = name;
		this.budget = budget;
	}

	public static Ledger create(Folder folder, String name, Long budget) {
		return new Ledger(folder.getGroup(), folder, name, budget);
	}

	public void rename(String name) {
		this.name = name;
	}

	/** 폴더 이동. 같은 모임 안에서만 허용되며 검증은 Service 에서 한다. null 이면 최상위 영역으로 올린다. */
	public void moveTo(Folder folder) {
		this.folder = folder;
	}

	public Long getFolderId() {
		return this.folder == null ? null : this.folder.getId();
	}

	public void changeBudget(Long budget) {
		this.budget = budget;
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
