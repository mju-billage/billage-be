package com.billage.group.dto;

/**
 * 모임 삭제 확인(화면의 "모임명을 입력해 주세요"). 본문 자체는 선택이다 —
 * 본문 없이 부르던 기존 앱을 깨지 않기 위해서이며, 값이 오면 반드시 대조한다.
 *
 * @param confirmName 사용자가 입력한 모임명. 실제 모임명과 같아야 삭제된다.
 */
public record GroupDeleteRequest(String confirmName) {
}
