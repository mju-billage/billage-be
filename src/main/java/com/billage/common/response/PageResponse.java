package com.billage.common.response;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 목록 페이지네이션 공통 응답. Spring 의 Page 를 그대로 노출하지 않고 합의된 필드만 내려준다.
 */
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last
) {

	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(
				page.getContent().stream().map(mapper).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast());
	}
}
