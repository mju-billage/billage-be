package com.billage.helpcenter;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.mail.MailSender;
import com.billage.common.response.PageResponse;
import com.billage.helpcenter.dto.FaqResponse;
import com.billage.helpcenter.dto.InquiryCreateRequest;
import com.billage.helpcenter.dto.NoticeDetailResponse;
import com.billage.helpcenter.dto.NoticeSummaryResponse;
import com.billage.helpcenter.dto.TermsResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 고객지원(더보기 &gt; 설정): FAQ·공지·약관 조회와 문의 접수.
 *
 * <p>모임이 아니라 서비스 전역 데이터라 모임 권한 검사가 없다. FAQ·공지·약관은 로그인 없이도 열 수 있다.
 * 백오피스가 없어 내용은 Flyway 마이그레이션으로만 넣고 고친다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelpCenterService {

	private final FaqRepository faqRepository;
	private final NoticeRepository noticeRepository;
	private final TermsRepository termsRepository;
	private final InquiryRepository inquiryRepository;
	private final MailSender mailSender;
	private final HelpCenterProperties properties;

	@Transactional(readOnly = true)
	public List<FaqResponse> getFaqs() {
		return faqRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(FaqResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public PageResponse<NoticeSummaryResponse> getNotices(Pageable pageable) {
		return PageResponse.of(noticeRepository.findAll(pageable), NoticeSummaryResponse::from);
	}

	@Transactional(readOnly = true)
	public NoticeDetailResponse getNotice(Long noticeId) {
		return noticeRepository.findById(noticeId)
				.map(NoticeDetailResponse::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
	}

	@Transactional(readOnly = true)
	public TermsResponse getTerms(TermType termType) {
		return termsRepository.findById(termType)
				.map(TermsResponse::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.TERMS_NOT_FOUND));
	}

	/**
	 * 문의 접수. 저장한 뒤 운영자 주소가 설정돼 있으면 커밋 후 알림 메일을 보낸다.
	 * 메일 실패는 접수 실패가 아니다 — 문의는 이미 저장됐으니 로그만 남긴다.
	 */
	@Transactional
	public void createInquiry(Long userId, InquiryCreateRequest request) {
		String title = requireNonBlank(request.title(), "문의 제목");
		String content = requireNonBlank(request.content(), "문의 내용");
		Inquiry inquiry = inquiryRepository.save(Inquiry.create(userId, request.email().trim(), title, content));

		if (properties.notifiesByMail()) {
			notifyAfterCommit(inquiry);
		}
	}

	private void notifyAfterCommit(Inquiry inquiry) {
		String subject = "[빌리지 문의] " + inquiry.getTitle();
		String body = "보낸 사람: " + inquiry.getEmail() + "\n문의 번호: " + inquiry.getId()
				+ "\n\n" + inquiry.getContent();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					mailSender.send(properties.inquiryEmail(), subject, body);
				} catch (RuntimeException e) {
					log.warn("문의 알림 메일 발송 실패 inquiryId={}", inquiry.getId(), e);
				}
			}
		});
	}

	/** `@NotBlank` 는 컨트롤러 경로에만 걸리므로 Service 에서도 공백 전용 값을 막는다. */
	private String requireNonBlank(String value, String label) {
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, label + "은(는) 공백일 수 없습니다.");
		}
		return trimmed;
	}
}
