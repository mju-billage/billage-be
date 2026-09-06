package com.billage.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.security.CurrentUserId;
import com.billage.common.response.ApiResponse;
import com.billage.user.dto.MyProfileResponse;
import com.billage.user.dto.PasswordChangeRequest;
import com.billage.user.dto.ProfileUpdateRequest;
import com.billage.user.dto.WithdrawRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 내 계정(「더보기 &gt; 설정」). 인증 흐름(로그인·토큰)은 {@code AuthController} 가 맡고
 * 여기서는 로그인한 사용자 본인의 정보만 다룬다. 경로는 노션 명세의 정정(2026-08-30)에 따라
 * {@code /api/v1/users} 가 아니라 {@code /api/v1/auth/me} 를 쓴다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<MyProfileResponse>> me(@CurrentUserId Long userId) {
		return ResponseEntity.ok(
				ApiResponse.of(userService.getMyProfile(userId), "내 정보 조회에 성공했습니다."));
	}

	@PatchMapping("/me")
	public ResponseEntity<ApiResponse<MyProfileResponse>> updateMe(@CurrentUserId Long userId,
			@Valid @RequestBody ProfileUpdateRequest request) {
		return ResponseEntity.ok(
				ApiResponse.of(userService.updateMyProfile(userId, request), "내 정보 수정에 성공했습니다."));
	}

	@PatchMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@CurrentUserId Long userId,
			@Valid @RequestBody PasswordChangeRequest request) {
		userService.changePassword(userId, request);
	}

	/**
	 * 회원 탈퇴. 권한 이전과 사유를 함께 받으므로 DELETE 지만 본문이 있다.
	 */
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@CurrentUserId Long userId, @Valid @RequestBody WithdrawRequest request) {
		userService.withdraw(userId, request);
	}
}
