package com.billage.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 프레임워크 예외의 상태코드 계약.
 *
 * <p>{@link GlobalExceptionHandler} 는 {@code @ExceptionHandler(Exception.class)} catch-all 을 들고 있어서,
 * 구체 핸들러가 없으면 원래 4xx 로 나가야 할 Spring MVC 예외가 전부 500 {@code INTERNAL_ERROR} 로 나간다
 * ({@code ExceptionHandlerExceptionResolver}(order 0)가 {@code DefaultHandlerExceptionResolver}(order 2)보다
 * 먼저 돌기 때문). 컨트롤러별 응답 형태가 아니라 <b>전 API 가 공유하는 에러 계약</b>이라 회귀를 막아둔다.
 */
class GlobalExceptionHandlerTest {

	@RestController
	static class ProbeController {

		@GetMapping("/probe/required-param")
		String requiredParam(@RequestParam String must) {
			return must;
		}

		@PostMapping("/probe/upload")
		String upload(@RequestParam("file") String file) {
			return file;
		}

		@GetMapping("/probe/upload-too-big")
		String uploadTooBig() {
			throw new MaxUploadSizeExceededException(12_000_000L);
		}

		@GetMapping("/probe/no-resource")
		String noResource() throws NoResourceFoundException {
			throw new NoResourceFoundException(HttpMethod.GET, "/api/v1/nope", "/api/v1/nope");
		}
	}

	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new ProbeController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	@DisplayName("매핑되지 않은 경로는 404 RESOURCE_NOT_FOUND")
	void noResourceFound() throws Exception {
		mockMvc.perform(get("/probe/no-resource"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	@DisplayName("허용되지 않은 메서드는 405 METHOD_NOT_ALLOWED")
	void methodNotSupported() throws Exception {
		mockMvc.perform(post("/probe/required-param"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	@DisplayName("필수 쿼리파라미터 누락은 400 INVALID_REQUEST")
	void missingRequestParameter() throws Exception {
		mockMvc.perform(get("/probe/required-param"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("필수 멀티파트 파트 누락은 400 INVALID_REQUEST")
	void missingRequestPart() throws Exception {
		mockMvc.perform(multipart("/probe/upload"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("컨테이너 업로드 상한 초과는 413 FILE_SIZE_EXCEEDED — FileService 정책 상한과 같은 코드로 응답한다")
	void maxUploadSizeExceeded() throws Exception {
		mockMvc.perform(get("/probe/upload-too-big"))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("FILE_SIZE_EXCEEDED"));
	}
}
