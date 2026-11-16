package com.billage.notification;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;
import com.billage.common.response.PageResponse;
import com.billage.membership.GroupMembership;
import com.billage.membership.GroupMembershipRepository;
import com.billage.notification.dto.NotificationResponse;
import com.billage.notification.dto.NotificationSettingResponse;
import com.billage.notification.dto.NotificationSettingUpdateRequest;
import com.billage.user.User;
import com.billage.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 앱 안 알림(푸시 없음). 각 도메인 서비스가 자기 트랜잭션 안에서 직접 {@code notify*} 를 부른다 —
 * 이벤트 버스·비동기를 두지 않으므로 본 작업이 롤백되면 알림도 함께 사라진다.
 *
 * <p>받는 사람이 그 카테고리를 꺼 두었으면 알림을 <b>만들지 않는다</b>(화면명세: 꺼 둔 카테고리는 목록에
 * 적재되지 않음). 행위자 본인에게는 보내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final NotificationSettingRepository settingRepository;
	private final GroupMembershipRepository groupMembershipRepository;
	private final UserRepository userRepository;

	// --- 보내기 ---

	/** 모임 관리자 전원(행위자 제외)에게. */
	@Transactional
	public void notifyManagers(Long groupId, Long actorUserId, NotificationMessage message) {
		send(recipientsOf(groupId, actorUserId, false), message);
	}

	/** 모임 총무(행위자 제외)에게. */
	@Transactional
	public void notifyOwners(Long groupId, Long actorUserId, NotificationMessage message) {
		send(recipientsOf(groupId, actorUserId, true), message);
	}

	/** 한 사람에게. 행위자 본인이면 보내지 않는다. */
	@Transactional
	public void notifyUser(Long userId, Long actorUserId, NotificationMessage message) {
		if (userId == null || userId.equals(actorUserId)) {
			return;
		}
		send(List.of(userId), message);
	}

	private List<Long> recipientsOf(Long groupId, Long actorUserId, boolean ownersOnly) {
		return groupMembershipRepository.findByGroupId(groupId).stream()
				.filter(membership -> !ownersOnly || membership.isOwner())
				.map(GroupMembership::getUserId)
				.filter(userId -> !userId.equals(actorUserId))
				.toList();
	}

	private void send(Collection<Long> userIds, NotificationMessage message) {
		if (userIds.isEmpty()) {
			return;
		}
		// 탈퇴한 사용자(예: 승인 대기 내역을 남기고 탈퇴한 등록자)에게 보내면 FK 에 걸려 본 작업까지 롤백된다.
		// 남아 있는 계정에만 보낸다.
		Set<Long> existing = userRepository.findAllById(userIds).stream().map(User::getId).collect(Collectors.toSet());
		Map<Long, NotificationSetting> settings = settingRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(NotificationSetting::getUserId, Function.identity()));
		List<Notification> notifications = userIds.stream()
				.distinct()
				.filter(existing::contains)
				.filter(userId -> settings.getOrDefault(userId, NotificationSetting.defaults(userId))
						.receives(message.category()))
				.map(userId -> Notification.create(userId, message))
				.toList();
		notificationRepository.saveAll(notifications);
	}

	// --- 조회·읽음 ---

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
		return PageResponse.of(notificationRepository.findByUserId(userId, pageable), NotificationResponse::from);
	}

	/** 읽음 처리. 남의 알림은 없는 것과 같이 404. 이미 읽었으면 그대로 둔다. */
	@Transactional
	public void markRead(Long notificationId, Long userId) {
		notificationRepository.findByIdAndUserId(notificationId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND))
				.markRead(LocalDateTime.now());
	}

	/** 대시보드 종 아이콘 배지. 모임 구분 없이 사용자 기준이다. */
	@Transactional(readOnly = true)
	public boolean hasUnread(Long userId) {
		return notificationRepository.existsByUserIdAndReadAtIsNull(userId);
	}

	// --- 설정 ---

	@Transactional(readOnly = true)
	public NotificationSettingResponse getSettings(Long userId) {
		User user = findUser(userId);
		return NotificationSettingResponse.of(settingOf(userId), user.getMarketingAgreedAt() != null);
	}

	/**
	 * 설정 부분 수정. 토글 하나씩 즉시 저장하는 화면이라 요청이 겹칠 수 있다 — 사용자 행을 잠가 같은 사람의 수정을
	 * 줄 세운다. 잠그지 않으면 두 요청이 같은 원래 값을 읽고 서로의 변경을 덮어쓰고, 첫 저장 때는 행 생성이 겹쳐
	 * PK 충돌이 난다.
	 */
	@Transactional
	public NotificationSettingResponse updateSettings(Long userId, NotificationSettingUpdateRequest request) {
		User user = userRepository.findByIdForUpdate(Objects.requireNonNull(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		NotificationSetting setting = settingRepository.findById(userId)
				.orElseGet(() -> settingRepository.save(NotificationSetting.defaults(userId)));
		setting.update(request.groupActivity(), request.approval(), request.dues(), request.noticeAndUpdate(),
				request.nightTime());

		// 마케팅은 가입 동의값 하나로 관리한다. 값이 바뀔 때만 기록해 처음 동의한 시각을 덮어쓰지 않는다.
		boolean marketing = user.getMarketingAgreedAt() != null;
		if (request.marketing() != null && request.marketing() != marketing) {
			user.recordMarketingAgreement(request.marketing(), LocalDateTime.now());
			marketing = request.marketing();
		}
		return NotificationSettingResponse.of(setting, marketing);
	}

	private NotificationSetting settingOf(Long userId) {
		return settingRepository.findById(userId).orElseGet(() -> NotificationSetting.defaults(userId));
	}

	private User findUser(Long userId) {
		return userRepository.findById(Objects.requireNonNull(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}
}
