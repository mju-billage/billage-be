package com.billage.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.dto.LoginRequest;
import com.billage.auth.dto.LoginResponse;
import com.billage.auth.dto.LogoutRequest;
import com.billage.auth.dto.RefreshRequest;
import com.billage.auth.dto.SocialLoginRequest;
import com.billage.auth.dto.SocialLoginResponse;
import com.billage.auth.dto.SocialSignupRequest;
import com.billage.auth.dto.TokenResponse;
import com.billage.auth.dto.UserResponse;
import com.billage.auth.social.SocialAuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final SocialAuthService socialAuthService;

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.login(request.email(), request.password()));
	}

	/**
	 * 간편 로그인. 이미 연결된 계정이면 바로 토큰을 발급하고, 최초 로그인이면
	 * {@code SIGNUP_REQUIRED}와 이메일만 반환해 클라이언트가 약관 동의·이름 입력 화면으로 안내하게 한다.
	 */
	@PostMapping("/social/login")
	public ResponseEntity<SocialLoginResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
		return ResponseEntity.ok(socialAuthService.login(request.provider(), request.token()));
	}

	/**
	 * 간편 회원가입. 약관 동의 후 이름을 입력받아 가입을 완료하고 즉시 로그인 처리한다.
	 */
	@PostMapping("/social/signup")
	public ResponseEntity<LoginResponse> socialSignup(@Valid @RequestBody SocialSignupRequest request) {
		LoginResponse response = socialAuthService.signup(request.provider(), request.token(), request.name());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ResponseEntity.ok(authService.refresh(request.refreshToken()));
	}

	@GetMapping("/me")
	public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
		Long userId = Long.valueOf(jwt.getSubject());
		return ResponseEntity.ok(authService.getCurrentUser(userId));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
	}
}
