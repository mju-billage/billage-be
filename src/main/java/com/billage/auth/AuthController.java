package com.billage.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.billage.auth.dto.LoginRequest;
import com.billage.auth.dto.LoginResponse;
import com.billage.auth.dto.LogoutRequest;
import com.billage.auth.dto.RefreshRequest;
import com.billage.auth.dto.SignupRequest;
import com.billage.auth.dto.SignupResponse;
import com.billage.auth.dto.SocialLoginRequest;
import com.billage.auth.dto.SocialLoginResponse;
import com.billage.auth.dto.SocialSignupRequest;
import com.billage.auth.dto.TokenResponse;
import com.billage.auth.social.SocialAuthService;
import com.billage.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final SocialAuthService socialAuthService;

	/**
	 * 이메일 회원가입. 명세상 가입과 로그인은 분리돼 있어 토큰을 발급하지 않는다 —
	 * 클라이언트는 성공 후 로그인을 별도로 호출한다.
	 */
	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
		SignupResponse response = authService.signup(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "회원가입에 성공했습니다."));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request.email(), request.password());
		return ResponseEntity.ok(ApiResponse.of(response, "로그인에 성공했습니다."));
	}

	/**
	 * 간편 로그인. 이미 연결된 계정이면 바로 토큰을 발급하고, 최초 로그인이면
	 * {@code SIGNUP_REQUIRED}와 이메일만 반환해 클라이언트가 약관 동의·이름 입력 화면으로 안내하게 한다.
	 */
	@PostMapping("/social/login")
	public ResponseEntity<ApiResponse<SocialLoginResponse>> socialLogin(
			@Valid @RequestBody SocialLoginRequest request) {
		SocialLoginResponse response = socialAuthService.login(request.provider(), request.token());
		String message = response.login() == null ? "회원가입이 필요합니다." : "로그인에 성공했습니다.";
		return ResponseEntity.ok(ApiResponse.of(response, message));
	}

	/**
	 * 간편 회원가입. 약관 동의 후 이름을 입력받아 가입을 완료하고 즉시 로그인 처리한다.
	 */
	@PostMapping("/social/signup")
	public ResponseEntity<ApiResponse<LoginResponse>> socialSignup(@Valid @RequestBody SocialSignupRequest request) {
		LoginResponse response = socialAuthService.signup(request.provider(), request.token(), request.name());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(response, "회원가입에 성공했습니다."));
	}

	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
		TokenResponse response = authService.refresh(request.refreshToken());
		return ResponseEntity.ok(ApiResponse.of(response, "토큰 재발급에 성공했습니다."));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
	}
}
