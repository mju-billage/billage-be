package com.billage.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger(OpenAPI) 문서 설정.
 * <p>
 * Access Token(JWT) Bearer 인증 스킴을 등록해, Swagger UI 의 "Authorize" 버튼으로
 * {@code Authorization: Bearer {accessToken}} 헤더를 넣어 보호 API를 테스트할 수 있게 한다.
 * 배포 서버는 HTTPS 리버스 프록시(Caddy) 뒤에 있으므로 문서/서버 URL 은
 * {@code server.forward-headers-strategy} 로 프록시 헤더를 존중해 https 로 생성된다.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	public OpenAPI billageOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Billage API")
						.description("동아리·소모임 회비/수입/지출 관리 서비스 REST API")
						.version("v1"))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
				.components(new Components()
						.addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
								.name(BEARER_SCHEME)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("로그인/재발급으로 발급받은 Access Token 을 입력하세요.")));
	}
}
