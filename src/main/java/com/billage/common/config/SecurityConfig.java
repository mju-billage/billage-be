package com.billage.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.billage.auth.security.JwtAccessDeniedHandler;
import com.billage.auth.security.JwtAuthenticationEntryPoint;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtDecoder jwtDecoder;
	private final JwtAuthenticationEntryPoint authenticationEntryPoint;
	private final JwtAccessDeniedHandler accessDeniedHandler;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// 인증 없이 접근: 로그인·회원가입·재발급·로그아웃(로그아웃은 Refresh Token 자체로 검증)
						.requestMatchers(HttpMethod.POST,
								"/api/v1/auth/signup",
								"/api/v1/auth/login",
								"/api/v1/auth/social/login",
								"/api/v1/auth/social/signup",
								"/api/v1/auth/refresh",
								"/api/v1/auth/logout").permitAll()
						.requestMatchers(
								"/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
								"/actuator/health/**").permitAll()
						.anyRequest().authenticated())
				// Bearer JWT 리소스 서버 방식 인증.
				// BearerTokenAuthenticationFilter 는 전역 exceptionHandling 과 별개로 자체 EntryPoint 를
				// 사용하므로, 잘못된/만료 토큰도 공통 에러 형식으로 응답하도록 여기서 직접 지정한다.
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint(authenticationEntryPoint)
						.jwt(jwt -> jwt.decoder(jwtDecoder)))
				// 401/403을 공통 에러 형식으로 통일
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
