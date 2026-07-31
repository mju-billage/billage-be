package com.billage.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 의존성 없는 JDK HttpClient 기반 통합 테스트용 HTTP 클라이언트.
 * (RestAssured GET 요청이 이 환경의 Groovy 런타임과 충돌해 대체함.)
 */
public class HttpTestClient {

	private final HttpClient client = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String baseUrl;

	public HttpTestClient(int port) {
		this.baseUrl = "http://localhost:" + port;
	}

	public record Response(int status, Map<String, Object> body) {

		public Object at(String path) {
			Object current = body;
			for (String key : path.split("\\.")) {
				current = ((Map<?, ?>) current).get(key);
			}
			return current;
		}
	}

	public Response postJson(String path, Map<String, ?> body) {
		return send(request(path).POST(jsonBody(body)).header("Content-Type", "application/json"));
	}

	public Response get(String path, String bearerToken) {
		HttpRequest.Builder builder = request(path).GET();
		if (bearerToken != null) {
			builder.header("Authorization", "Bearer " + bearerToken);
		}
		return send(builder);
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Accept", "application/json");
	}

	private HttpRequest.BodyPublisher jsonBody(Map<String, ?> body) {
		try {
			return HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private Response send(HttpRequest.Builder builder) {
		try {
			HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			Map<String, Object> body = (response.body() == null || response.body().isBlank())
					? Map.of()
					: objectMapper.readValue(response.body(), Map.class);
			return new Response(response.statusCode(), body);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
