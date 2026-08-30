package com.billage.dues;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.response.PageResponse;
import com.billage.dues.dto.DuesCloseResponse;
import com.billage.dues.dto.DuesCreateRequest;
import com.billage.dues.dto.DuesCreateResponse;
import com.billage.dues.dto.DuesDetailResponse;
import com.billage.dues.dto.DuesSummaryResponse;
import com.billage.dues.dto.DuesTargetResponse;
import com.billage.dues.dto.DuesUpdateRequest;
import com.billage.dues.dto.DuesUpdateResponse;
import com.billage.dues.dto.PaymentStatusBulkUpdateRequest;
import com.billage.dues.dto.PaymentStatusBulkUpdateResponse;
import com.billage.dues.dto.PaymentStatusUpdateRequest;
import com.billage.dues.dto.PaymentStatusUpdateResponse;
import com.billage.entry.Entry;
import com.billage.entry.EntryRepository;
import com.billage.entry.EntryType;
import com.billage.ledger.Ledger;
import com.billage.ledger.LedgerRepository;
import com.billage.member.Member;
import com.billage.member.MemberRepository;
import com.billage.membership.GroupAccessGuard;
import com.billage.membership.GroupMembership;
import com.billage.user.User;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회비(납부 관리). 조회는 모임 관리자면 가능하고 <b>생성·수정·삭제·마감·납부 상태 변경은 총무 전용</b>이다.
 *
 * <p>거래내역 자동 등록이 없는 런칭 버전에서 이 기능은 총무가 수기로 정리하는 보조 도구다.
 * 납부 완료 전환도 실제 입금과 연동되지 않으므로, 정합성을 이유로 총무의 조작을 막지 않는다 —
 * 대상자를 빼서 총액이 달라지는 것도 정상 동작이며 보정은 총무가 수기로 한다(기획 확정).
 */
@Service
@RequiredArgsConstructor
public class DuesService {

	private final DuesRepository duesRepository;
	private final DuesMemberRepository duesMemberRepository;
	private final MemberRepository memberRepository;
	private final LedgerRepository ledgerRepository;
	private final EntryRepository entryRepository;
	private final UserRepository userRepository;
	private final GroupAccessGuard guard;

	@Transactional(readOnly = true)
	public PageResponse<DuesSummaryResponse> getDuesList(Long groupId, Long userId, DuesStatus status,
			String keyword, Pageable pageable) {
		guard.requireMembership(groupId, userId);

		// 화면의 '납부 예정' 탭은 저장 상태가 아니라 시작일로 갈린다 — DuesRepository.search 주석 참고.
		DuesStatus persistedStatus = status == DuesStatus.SCHEDULED ? DuesStatus.OPEN : status;
		Boolean started = switch (status) {
			case null -> null;
			case SCHEDULED -> Boolean.FALSE;
			case OPEN -> Boolean.TRUE;
			case CLOSED -> null;
		};
		String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

		Page<Dues> page = duesRepository.search(groupId, persistedStatus, started, normalizedKeyword,
				LocalDate.now(), pageable);
		List<Long> duesIds = page.getContent().stream().map(Dues::getId).toList();
		Map<Long, Map<PaymentStatus, Long>> counts = duesMemberRepository.countByDues(duesIds);
		Map<Long, String> ledgerNames = ledgerNamesOf(page.getContent());

		return PageResponse.of(page, dues -> {
			Map<PaymentStatus, Long> byStatus = counts.getOrDefault(dues.getId(), Map.of());
			long paid = byStatus.getOrDefault(PaymentStatus.PAID, 0L);
			long total = paid + byStatus.getOrDefault(PaymentStatus.UNPAID, 0L);
			return DuesSummaryResponse.of(dues, paid, total, ledgerNames.get(dues.getLedgerId()));
		});
	}

	@Transactional
	public DuesCreateResponse create(Long groupId, Long userId, DuesCreateRequest request) {
		guard.requireOwner(groupId, userId);
		Ledger ledger = findLedgerInGroup(request.ledgerId(), groupId);
		List<Member> targets = findMembersInGroup(request.targetMemberIds(), groupId);

		if (request.startDate().isAfter(request.dueDate())) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		Dues dues = duesRepository.save(Dues.create(groupId, request.title().trim(), request.amount(),
				request.startDate(), request.dueDate(), ledger.getId(), targets));

		return DuesCreateResponse.from(dues);
	}

	@Transactional(readOnly = true)
	public DuesDetailResponse getDetail(Long duesId, Long userId) {
		Dues dues = findDues(duesId);
		guard.requireMembership(dues.getGroupId(), userId);

		return DuesDetailResponse.of(dues, ledgerNameOf(dues.getLedgerId()));
	}

	/**
	 * 회비 수정. 마감 전이면 금액을 제외한 모든 값을 바꿀 수 있다.
	 * 이미 납부 완료인 대상자를 빼는 것도 막지 않는다 — 총액이 달라지는 것이 정상이다.
	 */
	@Transactional
	public DuesUpdateResponse update(Long duesId, Long userId, DuesUpdateRequest request) {
		Dues dues = findDues(duesId);
		guard.requireOwner(dues.getGroupId(), userId);
		dues.requireOpen();
		if (request.amount() != null) {
			throw new BusinessException(ErrorCode.DUES_AMOUNT_IMMUTABLE);
		}

		if (request.title() != null) {
			dues.updateTitle(requireNonBlank(request.title()));
		}
		if (request.startDate() != null || request.dueDate() != null) {
			dues.updatePeriod(request.startDate(), request.dueDate());
		}
		if (request.ledgerId() != null) {
			dues.moveToLedger(findLedgerInGroup(request.ledgerId(), dues.getGroupId()).getId());
		}
		if (request.targetMemberIds() != null) {
			dues.replaceTargets(findMembersInGroup(request.targetMemberIds(), dues.getGroupId()));
		}

		return DuesUpdateResponse.from(dues);
	}

	/**
	 * 회비 삭제. 납부자가 있거나 마감된 이후에도 지울 수 있다.
	 * 마감으로 만들어진 수입 내역은 <b>건드리지 않는다</b> — 마감 즉시 서로 독립된 데이터다(기획 확정).
	 */
	@Transactional
	public void delete(Long duesId, Long userId) {
		Dues dues = findDues(duesId);
		guard.requireOwner(dues.getGroupId(), userId);

		duesRepository.delete(dues);
	}

	@Transactional(readOnly = true)
	public List<DuesTargetResponse> getTargets(Long duesId, Long userId, PaymentStatus status, String keyword) {
		Dues dues = findDues(duesId);
		guard.requireMembership(dues.getGroupId(), userId);

		String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

		return duesMemberRepository.findTargets(duesId, status, normalizedKeyword).stream()
				.map(target -> DuesTargetResponse.of(dues, target))
				.toList();
	}

	/** 납부 상태 변경(총무 전용). 자동 입금 매칭 없이 총무가 직접 바꾼다. */
	@Transactional
	public PaymentStatusUpdateResponse changePaymentStatus(Long duesId, Long memberId, Long userId,
			PaymentStatusUpdateRequest request) {
		Dues dues = findDues(duesId);
		guard.requireOwner(dues.getGroupId(), userId);

		Member member = memberRepository.findByIdAndGroupId(memberId, dues.getGroupId())
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		dues.changePaymentStatus(member, parseStatus(request.status()));

		return PaymentStatusUpdateResponse.of(dues, dues.getTargets().stream()
				.filter(target -> target.getMember().getId().equals(memberId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND)));
	}

	/**
	 * 납부 상태 일괄 변경(총무 전용). 화면이 여러 명을 체크한 뒤 버튼 한 번으로 처리한다.
	 *
	 * <p>이미 요청한 상태인 사람은 조용히 넘어가고 {@code changedCount} 에 세지 않는다 —
	 * 전체 선택으로 눌렀을 때 "0명의 납부가 확인되었어요" 같은 문구가 나오지 않도록 하기 위함이다.
	 */
	@Transactional
	public PaymentStatusBulkUpdateResponse changePaymentStatuses(Long duesId, Long userId,
			PaymentStatusBulkUpdateRequest request) {
		Dues dues = findDues(duesId);
		guard.requireOwner(dues.getGroupId(), userId);
		PaymentStatus status = parseStatus(request.status());

		List<Member> members = findMembersInGroup(request.memberIds(), dues.getGroupId());
		int changed = 0;
		for (Member member : members) {
			if (dues.changePaymentStatus(member, status)) {
				changed++;
			}
		}

		return PaymentStatusBulkUpdateResponse.of(dues, changed, status);
	}

	/**
	 * 회비 마감(총무 전용). 선택한 장부에 회비 제목과 <b>그때까지 실제로 걷힌 금액</b>으로 수입 내역 1건을 만든다.
	 * 총무의 행위이므로 그 내역은 즉시 승인 상태다.
	 *
	 * <p>미납자가 남아 있어도 마감한다 — 근거는 {@link Dues#close(Long)} 참고.
	 * 다만 한 명도 내지 않아 걷힌 금액이 0원이면 내역을 만들지 않는다. 금액은 1원 이상이어야 하고
	 * (엔티티 검증과 {@code ck_entry_amount} 제약), 0원짜리 수입은 장부에도 의미가 없다.
	 * 이 경우 {@code generatedEntryId} 는 {@code null} 로 남는다.
	 */
	@Transactional
	public DuesCloseResponse close(Long duesId, Long userId) {
		Dues dues = findDues(duesId);
		GroupMembership owner = guard.requireOwner(dues.getGroupId(), userId);
		dues.requireOpen();

		Ledger ledger = findLedgerInGroup(dues.getLedgerId(), dues.getGroupId());
		long collected = dues.totalCollectedAmount();
		Long generatedEntryId = null;
		if (collected > 0) {
			Entry entry = entryRepository.save(Entry.create(ledger, owner, userName(userId), EntryType.INCOME,
					dues.getTitle(), collected, LocalDate.now(), null));
			// 내역 쪽에도 회비를 표시해 둔다. 목록의 납부관리 아이콘과 상세 화면 분기가 이 값을 본다.
			entry.linkDues(dues.getId(), dues.getTitle());
			generatedEntryId = entry.getId();
		}
		dues.close(generatedEntryId);

		return DuesCloseResponse.from(dues);
	}

	/**
	 * 마감된 회비의 납부자 명단. 내역 상세(「상세 내역_납부관리_수입내역」)가 쓴다.
	 *
	 * <p>회비가 이미 삭제됐으면 {@link Optional#empty()} 다 — 마감 즉시 회비와 내역은 독립된 데이터가
	 * 되므로 내역만 남아 있는 상태가 정상이며, 이때 화면은 '회비 상세보기' 버튼을 숨긴다.
	 */
	@Transactional(readOnly = true)
	public Optional<List<DuesPayerView>> findPayers(Long duesId) {
		return duesRepository.findById(duesId)
				.map(dues -> duesMemberRepository.findTargets(duesId, PaymentStatus.PAID, null).stream()
						.map(target -> new DuesPayerView(target.getMember().getId(), target.getMember().getName(),
								dues.getAmount()))
						.toList());
	}

	/** 모임원을 명단에서 지울 때 그 사람의 회비 참여 데이터도 함께 지운다. */
	@Transactional
	public void deleteByMember(Long memberId) {
		duesMemberRepository.deleteAllByMemberId(memberId);
	}

	/** 모임 삭제용. 대상자는 cascade 로 함께 지워진다. */
	@Transactional
	public void deleteByGroup(Long groupId) {
		duesRepository.deleteAll(duesRepository.findAllByGroupId(groupId));
	}

	private Dues findDues(Long duesId) {
		return duesRepository.findById(duesId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DUES_NOT_FOUND));
	}

	/** 장부는 자유롭게 삭제될 수 있어, 마감 시점에 없으면 LEDGER_NOT_FOUND 다. */
	private Ledger findLedgerInGroup(Long ledgerId, Long groupId) {
		if (ledgerId == null) {
			throw new BusinessException(ErrorCode.LEDGER_NOT_FOUND);
		}
		Ledger ledger = ledgerRepository.findById(ledgerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.LEDGER_NOT_FOUND));
		if (!ledger.getGroup().getId().equals(groupId)) {
			throw new BusinessException(ErrorCode.GROUP_MISMATCH);
		}
		return ledger;
	}

	private List<Member> findMembersInGroup(List<Long> memberIds, Long groupId) {
		List<Long> distinctIds = memberIds.stream().distinct().toList();
		Map<Long, Member> found = new LinkedHashMap<>();
		memberRepository.findAllById(distinctIds).forEach(member -> found.put(member.getId(), member));

		return distinctIds.stream()
				.map(memberId -> {
					Member member = found.get(memberId);
					if (member == null) {
						throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
					}
					if (!member.getGroup().getId().equals(groupId)) {
						throw new BusinessException(ErrorCode.GROUP_MISMATCH);
					}
					return member;
				})
				.toList();
	}

	private Map<Long, String> ledgerNamesOf(List<Dues> duesList) {
		List<Long> ledgerIds = duesList.stream()
				.map(Dues::getLedgerId)
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();
		if (ledgerIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> names = new LinkedHashMap<>();
		ledgerRepository.findAllById(ledgerIds).forEach(ledger -> names.put(ledger.getId(), ledger.getName()));
		return names;
	}

	private String ledgerNameOf(Long ledgerId) {
		return ledgerId == null ? null
				: ledgerRepository.findById(ledgerId).map(Ledger::getName).orElse(null);
	}

	private PaymentStatus parseStatus(String status) {
		try {
			return PaymentStatus.valueOf(status.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
		}
	}

	private String requireNonBlank(String title) {
		if (title == null || title.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "회비 제목은 공백일 수 없습니다.");
		}
		return title.trim();
	}

	private String userName(Long userId) {
		return userRepository.findById(userId).map(User::getName)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}
}
