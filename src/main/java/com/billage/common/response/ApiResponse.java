package com.billage.common.response;

/**
 * 공통 성공 응답 래퍼. 프론트와 합의한 {@code {data, message}} 형식.
 * 본문이 없는 204 응답에는 사용하지 않는다.
 */
public record ApiResponse<T>(T data, String message) {

	public static <T> ApiResponse<T> of(T data, String message) {
		return new ApiResponse<>(data, message);
	}
}
