package com.billage.ocr;

/**
 * 영수증에서 읽은 품목 한 줄.
 *
 * <p>모든 필드가 null 일 수 있다 — 영수증마다 인쇄 형식이 달라 수량 없이 금액만 찍히거나
 * 단가 없이 합계만 찍히는 경우가 흔하다. 못 읽은 칸을 0 으로 채우면 사용자가 "0원짜리 품목"으로 읽게 되므로
 * 비운 채로 올려보내고 화면에서 채우게 한다.
 *
 * @param confidence 인식 신뢰도(0~1). 품목명 기준이다 — 사용자가 고칠지 판단하는 기준이 결국 품목명이라서다.
 */
public record ReceiptOcrItem(String name, Integer quantity, Long unitPrice, Long amount, Double confidence) {
}
