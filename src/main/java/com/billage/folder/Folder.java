package com.billage.folder;

import java.time.LocalDateTime;

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
 * 장부를 묶는 폴더. {@code parent} 가 null 이면 최상위 폴더이며 중첩을 지원한다.
 * 삭제 시 폴더만 제거하고 내부 장부·하위 폴더는 상위 폴더로 올린다.
 */
@Entity
@Table(name = "folder")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Folder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private GroupSpace group;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_folder_id")
	private Folder parent;

	@Column(nullable = false, length = 20)
	private String name;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Version
	private Long version;

	private Folder(GroupSpace group, Folder parent, String name) {
		this.group = group;
		this.parent = parent;
		this.name = name;
	}

	public static Folder create(GroupSpace group, Folder parent, String name) {
		return new Folder(group, parent, name);
	}

	public void rename(String name) {
		this.name = name;
	}

	/** 상위 폴더 이동. null 이면 최상위로 올린다. */
	public void moveTo(Folder parent) {
		this.parent = parent;
	}

	public Long getParentId() {
		return this.parent == null ? null : this.parent.getId();
	}

	/** 최상위부터의 깊이. 자기 참조 FK 때문에 삭제 순서를 정할 때 쓴다. */
	public int depth() {
		int depth = 0;
		for (Folder current = this.parent; current != null; current = current.getParent()) {
			depth++;
		}
		return depth;
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
