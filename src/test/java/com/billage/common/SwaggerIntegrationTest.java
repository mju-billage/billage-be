package com.billage.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.billage.support.HttpTestClient;
import com.billage.support.HttpTestClient.Response;
import com.billage.support.IntegrationTest;

/**
 * Swagger/OpenAPI 문서가 인증 없이 열리고(시큐리티 permit), JWT Bearer 스킴이 노출되는지 검증.
 */
class SwaggerIntegrationTest extends IntegrationTest {

	@LocalServerPort
	int port;

	private HttpTestClient http;

	@BeforeEach
	void setUp() {
		http = new HttpTestClient(port);
	}

	@Test
	void api_docs_는_인증없이_접근되고_bearer_스킴을_포함한다() {
		Response response = http.get("/v3/api-docs", null);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.at("info.title")).isEqualTo("Billage API");
		// JWT Bearer 보안 스킴이 문서에 등록되어 Swagger UI 에 Authorize 버튼이 노출됨
		Object bearer = response.at("components.securitySchemes.bearerAuth.scheme");
		assertThat(bearer).isEqualTo("bearer");
	}
}
