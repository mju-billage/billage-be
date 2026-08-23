package com.billage.member;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.billage.group.GroupSpace;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
 * 납부 관리·모임원 명단에 쓰는 사람 데이터. 이름만 필수이고 전화번호·메모·태그는 선택값이다.
 * 가입 사용자·관리자 관계({@code com.billage.membership.GroupMembership})와 별개이며 자동 연결하지 않는다.
 * 권한 값을 갖지 않는다.
 */
@Entity
@Table(name = "group_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

	/** 한 명에게 붙일 수 있는 태그 수. 화면이 칩으로 나열하는 구조라 과도한 입력만 막는다. */
	public static final int MAX_TAGS = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private GroupSpace group;

	@Column(nullable = false, length = 10)
	private String name;

	/** 숫자만 저장한다. 하이픈 표기는 화면에서 만든다. */
	@Column(name = "phone_number", length = 20)
	private String phoneNumber;

	@Column(length = 30)
	private String memo;

	/**
	 * 모임원마다 자유 입력하는 태그. 모임 단위 태그 마스터는 두지 않는다(기획 확정).
	 * (member_id, name) 복합 기본키라 같은 태그를 두 번 붙일 수 없다.
	 */
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "member_tag", joinColumns = @JoinColumn(name = "member_id"))
	@Column(name = "name", nullable = false, length = 10)
	private Set<String> tags = new LinkedHashSet<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Version
	private Long version;

	private Member(GroupSpace group, String name, String phoneNumber, String memo, Collection<String> tags) {
		this.group = group;
		this.name = name;
		this.phoneNumber = phoneNumber;
		this.memo = memo;
		replaceTags(tags);
	}

	public static Member create(GroupSpace group, String name, String phoneNumber, String memo,
			Collection<String> tags) {
		return new Member(group, name, phoneNumber, memo, tags);
	}

	/** 일괄 추가는 이름만 받는다. */
	public static Member create(GroupSpace group, String name) {
		return new Member(group, name, null, null, null);
	}

	/**
	 * 상세 수정 화면 저장. 화면이 항목 전체를 보내는 구조라 선택값은 부분 반영이 아니라 통째로 교체한다
	 * (보내지 않은 선택값은 비워진다). 회비 참여 데이터는 그대로 유지된다
	 * (지우고 다시 만들면 납부 기록이 함께 사라진다).
	 */
	public void update(String name, String phoneNumber, String memo, Collection<String> tags) {
		this.name = name;
		this.phoneNumber = phoneNumber;
		this.memo = memo;
		replaceTags(tags);
	}

	/** 정렬된 사본. 화면 노출 순서를 서버가 고정해 준다. */
	public List<String> sortedTags() {
		return this.tags.stream().sorted().toList();
	}

	private void replaceTags(Collection<String> newTags) {
		this.tags.clear();
		if (newTags != null) {
			this.tags.addAll(newTags);
		}
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
