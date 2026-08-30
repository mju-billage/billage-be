package com.billage.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

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
		return postJson(path, body, null);
	}

	public Response postJson(String path, Map<String, ?> body, String bearerToken) {
		return send(authorized(request(path), bearerToken)
				.POST(jsonBody(body))
				.header("Content-Type", "application/json"));
	}

	public Response patchJson(String path, Map<String, ?> body, String bearerToken) {
		return send(authorized(request(path), bearerToken)
				.method("PATCH", jsonBody(body))
				.header("Content-Type", "application/json"));
	}

	public Response delete(String path, String bearerToken) {
		return send(authorized(request(path), bearerToken).DELETE());
	}

	public Response get(String path, String bearerToken) {
		return send(authorized(request(path), bearerToken).GET());
	}

	/**
	 * 파일 하나를 담은 multipart 요청. 컨테이너의 업로드 상한처럼 <b>요청을 파싱하는 단계</b>에서 갈리는 동작은
	 * MockMvc 로는 재현되지 않아 실제 요청을 보내야 한다.
	 */
	public Response postMultipartFile(String path, String partName, String fileName, String contentType,
			byte[] content, Map<String, String> formFields, String bearerToken) {
		String boundary = "----billage-test-" + UUID.randomUUID();
		byte[] body = multipartBody(boundary, partName, fileName, contentType, content, formFields);
		return send(authorized(request(path), bearerToken)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary));
	}

	private byte[] multipartBody(String boundary, String partName, String fileName, String contentType,
			byte[] content, Map<String, String> formFields) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			for (Map.Entry<String, String> field : formFields.entrySet()) {
				out.write(("--" + boundary + "\r\n"
						+ "Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
						+ field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
			}
			out.write(("--" + boundary + "\r\n"
					+ "Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + fileName + "\"\r\n"
					+ "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
			out.write(content);
			out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return out.toByteArray();
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Accept", "application/json");
	}

	private HttpRequest.Builder authorized(HttpRequest.Builder builder, String bearerToken) {
		return bearerToken == null ? builder : builder.header("Authorization", "Bearer " + bearerToken);
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
