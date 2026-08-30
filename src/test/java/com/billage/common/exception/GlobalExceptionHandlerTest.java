package com.billage.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프레임워크 예외의 상태코드 계약.
 *
 * <p>{@link GlobalExceptionHandler} 는 {@code @ExceptionHandler(Exception.class)} catch-all 을 들고 있어서,
 * 구체 핸들러가 없으면 원래 4xx 로 나가야 할 Spring MVC 예외가 전부 500 {@code INTERNAL_ERROR} 로 나간다
 * ({@code ExceptionHandlerExceptionResolver}(order 0)가 {@code DefaultHandlerExceptionResolver}(order 2)보다
 * 먼저 돌기 때문). 컨트롤러별 응답 형태가 아니라 <b>전 API 가 공유하는 에러 계약</b>이라 회귀를 막아둔다.
 *
 * <p>미매핑 경로(404)와 업로드 상한 초과(413)는 요청을 파싱하거나 핸들러를 찾는 단계에서 갈려 MockMvc 로는
 * 재현되지 않는다. {@link ErrorContractIntegrationTest} 에서 실제 요청으로 확인한다.
 */
class GlobalExceptionHandlerTest {

	@RestController
	static class ProbeController {

		@GetMapping("/probe/required-param")
		String requiredParam(@RequestParam String must) {
			return must;
		}

		@PostMapping("/probe/upload")
		String upload(@RequestPart("file") MultipartFile file) {
			return file.getName();
		}
	}

	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new ProbeController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

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
}
