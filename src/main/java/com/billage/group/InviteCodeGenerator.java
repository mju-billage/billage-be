package com.billage.group;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 모임 초대 코드 생성기. 혼동하기 쉬운 문자(0/O, 1/I/L)를 제외한 대문자·숫자 8자리.
 * 유일성은 DB 제약으로 보장하며, 호출 측에서 충돌 시 재시도한다.
 */
@Component
public class InviteCodeGenerator {

	private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
	private static final int LENGTH = 8;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		StringBuilder sb = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
		}
		return sb.toString();
	}
}
