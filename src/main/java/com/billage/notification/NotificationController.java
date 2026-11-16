package com.billage.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.common.response.PageResponse;
import com.billage.notification.dto.NotificationResponse;
import com.billage.notification.dto.NotificationSettingResponse;
import com.billage.notification.dto.NotificationSettingUpdateRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping("/api/v1/notifications")
	public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
			@CurrentUserId Long userId,
			@PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
			Pageable pageable) {
		PageResponse<NotificationResponse> notifications = notificationService.getNotifications(userId, pageable);
		String message = notifications.content().isEmpty() ? "조회된 데이터가 없습니다." : "알림 조회에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(notifications, message));
	}

	@PatchMapping("/api/v1/notifications/{notificationId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markRead(@CurrentUserId Long userId, @PathVariable Long notificationId) {
		notificationService.markRead(notificationId, userId);
	}

	@GetMapping("/api/v1/notifications/settings")
	public ResponseEntity<ApiResponse<NotificationSettingResponse>> getSettings(@CurrentUserId Long userId) {
		return ResponseEntity.ok(ApiResponse.of(notificationService.getSettings(userId), "알림 설정 조회에 성공했습니다."));
	}

	@PatchMapping("/api/v1/notifications/settings")
	public ResponseEntity<ApiResponse<NotificationSettingResponse>> updateSettings(@CurrentUserId Long userId,
			@RequestBody NotificationSettingUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.of(notificationService.updateSettings(userId, request),
				"알림 설정 변경에 성공했습니다."));
	}
}
