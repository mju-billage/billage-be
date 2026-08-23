package com.billage.member;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.dues.DuesService;
import com.billage.group.GroupSpace;
import com.billage.member.dto.MemberBulkCreateRequest;
import com.billage.member.dto.MemberCreateRequest;
import com.billage.member.dto.MemberResponse;
import com.billage.member.dto.MemberUpdateRequest;
import com.billage.membership.GroupAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * 납부 관리용 모임원 명단. 가입 사용자·관리자 관계와 자동 연결하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

	/** 일괄 추가 1회 상한. 오입력한 긴 텍스트가 통째로 명단이 되는 사고를 막는다. */
	private static final int MAX_BULK_SIZE = 100;

	/** 일괄 추가 구분자: 쉼표·띄어쓰기·줄바꿈(기획 확정). */
	private static final String BULK_DELIMITER = "[,\\s]+";

	private static final int MAX_NAME_LENGTH = 10;

	/** 전화번호 입력 허용 문자. 표기용 하이픈·공백까지만 받는다. */
	private static final Pattern PHONE_ALLOWED = Pattern.compile("[0-9 -]+");

	private final MemberRepository memberRepository;
	private final DuesService duesService;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public List<MemberResponse> getMembers(Long groupId, Long userId, String keyword) {
		guard.requireMembership(groupId, userId);
		String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

		return memberRepository.search(groupId, normalizedKeyword).stream()
				.map(MemberResponse::from)
				.toList();
	}

	/** 개별 추가(총무 전용). 이름만 필수이고 전화번호·태그·메모는 선택값이다. */
	@Transactional
	public MemberResponse addMember(Long groupId, Long userId, MemberCreateRequest request) {
		GroupSpace group = guard.requireOwner(groupId, userId).getGroup();

		Member member = Member.create(group, requireName(request.name()), normalizePhone(request.phoneNumber()),
				normalizeMemo(request.memo()), normalizeTags(request.tags()));

		return MemberResponse.from(memberRepository.save(member));
	}

	/**
	 * 일괄 추가(총무 전용). 입력 텍스트를 쉼표·띄어쓰기·줄바꿈으로 잘라 각각을 모임원으로 저장한다.
	 * 동명이인을 허용하는 명단이라 중복 이름도 그대로 등록한다.
	 */
	@Transactional
	public List<MemberResponse> addMembers(Long groupId, Long userId, MemberBulkCreateRequest request) {
		GroupSpace group = guard.requireOwner(groupId, userId).getGroup();

		List<String> names = Arrays.stream(request.names().trim().split(BULK_DELIMITER))
				.filter(name -> !name.isBlank())
				.toList();
		if (names.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "추가할 모임원 이름을 입력해 주세요.");
		}
		if (names.size() > MAX_BULK_SIZE) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST,
					"한 번에 추가할 수 있는 모임원은 %d명까지입니다.".formatted(MAX_BULK_SIZE));
		}

		List<Member> members = new ArrayList<>();
		for (String name : names) {
			members.add(Member.create(group, requireName(name)));
		}

		return memberRepository.saveAll(members).stream()
				.map(MemberResponse::from)
				.toList();
	}

	/**
	 * 모임원 상세 수정(총무 전용). 수정 화면이 항목 전체를 보내므로 선택값은 통째로 교체한다.
	 * 회비 참여 데이터는 그대로 유지된다.
	 */
	@Transactional
	public MemberResponse updateMember(Long groupId, Long userId, Long memberId, MemberUpdateRequest request) {
		guard.requireOwner(groupId, userId);

		Member member = memberRepository.findByIdAndGroupId(memberId, groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		member.update(requireName(request.name()), normalizePhone(request.phoneNumber()),
				normalizeMemo(request.memo()), normalizeTags(request.tags()));

		return MemberResponse.from(member);
	}

	/**
	 * 모임원 삭제. 회비 참여 데이터까지 완전 삭제하며, 관리자 권한(GroupMembership)에는 영향을 주지 않는다.
	 */
	@Transactional
	public void removeMember(Long groupId, Long userId, Long memberId) {
		guard.requireOwner(groupId, userId);

		Member member = memberRepository.findByIdAndGroupId(memberId, groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		// 명단에서 지우면 그 사람의 회비 참여 데이터도 완전 삭제한다(기획 확정).
		duesService.deleteByMember(memberId);
		memberRepository.delete(member);
	}

	private String requireName(String name) {
		String trimmed = (name == null) ? "" : name.trim();
		if (trimmed.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "모임원 이름은 공백일 수 없습니다.");
		}
		if (trimmed.length() > MAX_NAME_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST,
					"모임원 이름은 %d자 이하여야 합니다: %s".formatted(MAX_NAME_LENGTH, trimmed));
		}
		return trimmed;
	}

	/**
	 * 하이픈·공백을 걷어내고 숫자만 저장한다. 표기 형식은 화면이 만든다.
	 * 빈 값은 "지움"으로 보고 null 로 둔다.
	 * 숫자·하이픈·공백 외의 문자가 섞이면 걷어내지 않고 거부한다 — 조용히 지우면
	 * {@code 010a1234b5678} 같은 오입력이 멀쩡한 번호로 저장된다.
	 */
	private String normalizePhone(String phoneNumber) {
		if (phoneNumber == null || phoneNumber.isBlank()) {
			return null;
		}
		String trimmed = phoneNumber.trim();
		if (!PHONE_ALLOWED.matcher(trimmed).matches()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "전화번호는 숫자와 하이픈만 사용할 수 있습니다.");
		}
		String digits = trimmed.replaceAll("[^0-9]", "");
		if (digits.length() < 9 || digits.length() > 11) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "전화번호 형식이 올바르지 않습니다.");
		}
		return digits;
	}

	private String normalizeMemo(String memo) {
		if (memo == null || memo.isBlank()) {
			return null;
		}
		return memo.trim();
	}

	/**
	 * 공백 제거 후 중복을 걸러 낸다. 빈 목록과 null 은 "태그 없음"으로 같게 다룬다.
	 * DB 는 (member_id, name) 복합 기본키로 중복을 막는데, 컬럼 collation 을
	 * {@code utf8mb4_0900_as_cs} 로 고정해 두었으므로 자바의 문자열 동등성과 판정이 일치한다.
	 * (기본 collation 인 {@code utf8mb4_0900_ai_ci} 였다면 "VIP" 와 "vip" 가 자바에서는 둘,
	 *  DB 에서는 하나로 갈려 duplicate key 500 이 난다.)
	 */
	private Set<String> normalizeTags(List<String> tags) {
		Set<String> normalized = new LinkedHashSet<>();
		if (tags == null) {
			return normalized;
		}
		for (String tag : tags) {
			if (tag == null || tag.isBlank()) {
				continue;
			}
			String trimmed = tag.trim();
			if (trimmed.length() > 10) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST, "태그는 10자 이하여야 합니다: " + trimmed);
			}
			normalized.add(trimmed);
		}
		if (normalized.size() > Member.MAX_TAGS) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST,
					"태그는 %d개 이하여야 합니다.".formatted(Member.MAX_TAGS));
		}
		return normalized;
	}
}
