package com.billage.auth.password;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 임시 비밀번호를 만든다. 가입 규칙(영문 대문자·소문자·숫자·특수문자 포함 8자 이상)을 항상 만족해야
 * 받은 값으로 로그인한 뒤 「비밀번호 변경」의 현재 비밀번호 칸에도 그대로 쓸 수 있다.
 *
 * <p>메일을 보고 손으로 옮겨 적는 값이라 서로 헷갈리는 글자(0·O, 1·l·I)는 뺐다.
 */
@Component
public class TemporaryPasswordGenerator {

	static final int LENGTH = 12;

	private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
	private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
	private static final String DIGIT = "23456789";
	private static final String SPECIAL = "!@#$%^&*";
	private static final String ALL = UPPER + LOWER + DIGIT + SPECIAL;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		List<Character> chars = new ArrayList<>(LENGTH);
		// 네 종류를 하나씩 먼저 넣어 규칙을 보장하고, 나머지는 전체에서 뽑는다.
		chars.add(pick(UPPER));
		chars.add(pick(LOWER));
		chars.add(pick(DIGIT));
		chars.add(pick(SPECIAL));
		while (chars.size() < LENGTH) {
			chars.add(pick(ALL));
		}
		// 앞 네 자리가 항상 같은 종류 순서가 되지 않게 섞는다.
		Collections.shuffle(chars, random);

		StringBuilder password = new StringBuilder(LENGTH);
		chars.forEach(password::append);
		return password.toString();
	}

	private char pick(String source) {
		return source.charAt(random.nextInt(source.length()));
	}
}
