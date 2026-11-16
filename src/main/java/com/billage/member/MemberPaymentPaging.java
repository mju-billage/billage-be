package com.billage.member;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

/**
 * 납부 내역 조회의 정렬·페이지 파라미터 해석.
 *
 * <p>정렬 기준은 납부 시각 하나만 받는다. 요청 값을 그대로 쿼리의 정렬 컬럼으로 넘기지 않으려고
 * 허용 값을 여기서 고정한다.
 */
final class MemberPaymentPaging {

	static final int DEFAULT_SIZE = 20;
	static final int MAX_SIZE = 100;

	private MemberPaymentPaging() {
	}

	static Pageable of(String sort, Integer page, Integer size) {
		Sort order = parseSort(sort);
		if (page == null && size == null) {
			return Pageable.unpaged(order);
		}
		int pageNumber = page == null ? 0 : page;
		int pageSize = size == null ? DEFAULT_SIZE : size;
		if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_SIZE) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST,
					"page 는 0 이상, size 는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
		}
		return PageRequest.of(pageNumber, pageSize, order);
	}

	private static Sort parseSort(String sort) {
		Sort.Direction direction;
		if (sort == null || sort.isBlank() || sort.equals("paidAt") || sort.equals("paidAt,desc")) {
			direction = Sort.Direction.DESC;
		} else if (sort.equals("paidAt,asc")) {
			direction = Sort.Direction.ASC;
		} else {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "sort 는 paidAt,desc 또는 paidAt,asc 만 쓸 수 있습니다.");
		}
		// 같은 시각에 확인된 납부가 페이지 경계에서 뒤섞이지 않게 ID 로 순서를 고정한다.
		return Sort.by(direction, "paidAt").and(Sort.by(direction, "id"));
	}
}
