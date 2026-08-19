package com.billage.ledger.dto;

/** 예산 수정. null 이면 예산 미설정으로 되돌린다. 범위 검증은 Service 에서 INVALID_BUDGET 으로 처리한다. */
public record BudgetUpdateRequest(Long budget) {
}
