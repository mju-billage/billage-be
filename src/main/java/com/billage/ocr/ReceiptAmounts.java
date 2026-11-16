package com.billage.ocr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 영수증 줄에서 <b>금액</b>을 읽는다. 총액({@link ReceiptTextParser})과 품목({@link ReceiptItemExtractor})이 같이 쓴다.
 *
 * <p>범용 OCR 은 숫자를 자주 틀리게 읽는데, 틀리는 방식이 정해져 있다 — 쉼표를 점으로({@code 15.000}),
 * 쉼표 뒤에 공백을 넣어서({@code 15, 000}), 숫자 0 을 알파벳 O 로({@code 15,O00}). 이걸 그대로 정규식에 넣으면
 * {@code 15} 만 남아 <b>15원</b>이 장부에 올라간다. 그래서 숫자를 읽기 전에 {@link #normalize} 로 되돌린다.
 *
 * <p>원화 영수증이라 소수점은 없다고 본다. 점 뒤에 정확히 세 자리가 오면 천 단위 구분자다.
 * 날짜({@code 2026.09.21}, {@code 26.09.21})는 점 뒤가 두 자리라 건드리지 않는다.
 */
final class ReceiptAmounts {

	/**
	 * 쉼표 없이 이 자릿수 이상이면 금액이 아니라 번호다(승인번호 {@code 30012345}·바코드·카드번호).
	 * 천만 원 이상을 쉼표 없이 찍는 영수증은 사실상 없고, 반대로 승인번호를 총액으로 집으면 금액이 수천 배로 뛴다.
	 */
	private static final int ID_LIKE_DIGITS = 8;

	/** "15, 000" → "15,000". 쉼표 뒤가 정확히 세 자리일 때만 붙인다. */
	private static final Pattern SPACED_COMMA = Pattern.compile("(\\d),\\s+(\\d{3})(?!\\d)");
	/**
	 * 숫자·쉼표·점과 O 로만 이뤄진 덩어리. 이 안의 O 는 0 이다. 영문자에 붙은 덩어리("500ml", "TOTAL")는
	 * 단어의 일부라 건드리지 않고, 한글은 경계로 본다("합계15.000원" 의 금액은 고쳐야 한다).
	 */
	private static final Pattern DIGITISH_TOKEN = Pattern.compile("(?<![A-Za-z\\d])[\\dOo][\\dOo,.]*(?![A-Za-z\\d])");
	/** "1.500" · "1.500.000" — 점이 천 단위 구분자로 쓰인 금액. */
	private static final Pattern DOT_THOUSANDS = Pattern.compile("^\\d{1,3}(?:\\.\\d{3})+$");

	/** 줄 안의 금액 후보. 앞에 붙은 부호(할인 {@code -2,000})까지 같이 읽는다. */
	private static final Pattern NUMBER = Pattern.compile("-?(?:\\d{1,3}(?:,\\d{3})+|\\d+)");
	/** 토큰 하나가 통째로 금액인지. "2,000원"·"₩2,000" 처럼 단위가 붙은 것도 금액이다. */
	private static final Pattern AMOUNT_TOKEN = Pattern.compile("^[₩\\\\]?(-?(?:\\d{1,3}(?:,\\d{3})+|\\d+))원?$");

	private ReceiptAmounts() {
	}

	/** 숫자 오인식을 되돌린 줄. 금액을 읽을 때만 쓴다 — 상호·날짜는 원문으로 읽는다. */
	static String normalize(String line) {
		String joined = SPACED_COMMA.matcher(line).replaceAll("$1,$2");
		Matcher matcher = DIGITISH_TOKEN.matcher(joined);
		StringBuilder normalized = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(normalized, Matcher.quoteReplacement(fixToken(matcher.group())));
		}
		matcher.appendTail(normalized);
		return normalized.toString();
	}

	private static String fixToken(String token) {
		// O 만으로 된 덩어리("O", "OO")는 글자다. 숫자가 하나라도 섞여야 0 으로 본다.
		if (!token.chars().anyMatch(Character::isDigit)) {
			return token;
		}
		String digits = token.replace('O', '0').replace('o', '0');
		return DOT_THOUSANDS.matcher(digits).matches() ? digits.replace('.', ',') : digits;
	}

	/**
	 * 줄에서 <b>가장 오른쪽의 양수 금액</b>. 영수증은 금액을 오른쪽 열에 찍고, 줄 안의 조각은 가로 위치 순이다.
	 * 번호처럼 보이는 숫자({@link #ID_LIKE_DIGITS})는 건너뛴다.
	 */
	static Long lastAmountOf(String line) {
		Matcher matcher = NUMBER.matcher(normalize(line));
		Long last = null;
		while (matcher.find()) {
			Long value = toAmount(matcher.group());
			if (value != null && value > 0) {
				last = value;
			}
		}
		return last;
	}

	/**
	 * 토큰 하나를 금액으로 읽는다. 금액이 아니면 null. 부호를 유지한다(할인 줄은 음수다).
	 * 토큰은 {@link #normalize} 를 거친 것이어야 한다.
	 */
	static Long parseToken(String token) {
		Matcher matcher = AMOUNT_TOKEN.matcher(token);
		return matcher.matches() ? toAmount(matcher.group(1)) : null;
	}

	private static Long toAmount(String number) {
		boolean negative = number.startsWith("-");
		String digits = (negative ? number.substring(1) : number);
		boolean grouped = digits.indexOf(',') >= 0;
		digits = digits.replace(",", "");
		if (digits.length() > 15 || (!grouped && digits.length() >= ID_LIKE_DIGITS)) {
			return null;
		}
		long value = Long.parseLong(digits);
		return negative ? -value : value;
	}
}
