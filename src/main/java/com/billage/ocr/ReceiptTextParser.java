package com.billage.ocr;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

/**
 * 영수증 줄글에서 상호·결제일·총액을 추려낸다. 범용 OCR 전용이다.
 *
 * <p><b>추측이 섞이는 코드다.</b> 영수증 서식이 가게마다 달라 규칙으로 100% 맞출 수 없다.
 * 그래서 확신이 서지 않으면 값을 지어내지 않고 비운다 — 총액만은 비울 수 없어(없으면 화면에 채울 게 없다)
 * 못 찾으면 {@code OCR_RESULT_EMPTY} 로 올린다.
 *
 * <p><b>이 클래스가 인식 품질을 결정한다.</b> 영수증 특화 모델을 쓰지 않기로 하면서(2026-09-21, 단가 문제)
 * 총액·결제일 판별이 통째로 여기 규칙에 달리게 됐다. 못 읽는 서식이 보고되면 키워드를 늘려 대응한다 —
 * 늘릴 때는 {@link #TOTAL_EXCLUSIONS} 와 부딪히지 않는지 함께 본다(구성 요소를 총액으로 집으면
 * 틀린 금액이 장부에 올라간다).
 */
final class ReceiptTextParser {

	/**
	 * 총액 줄을 가리키는 말. 영수증은 보통 맨 아래쪽에 최종 금액을 한 번 더 찍으므로
	 * <b>마지막으로 걸린 줄</b>을 쓴다("합계" 뒤에 "결제금액"이 또 나오는 서식이 흔하다).
	 */
	private static final List<String> TOTAL_KEYWORDS = List.of(
			"합계", "총액", "총계", "결제금액", "총결제금액", "받을금액", "청구금액", "판매금액", "총구매액");

	/** 총액처럼 보이지만 구성 요소인 줄. 이 말이 있으면 총액 줄로 보지 않는다. */
	private static final List<String> TOTAL_EXCLUSIONS = List.of("과세", "면세", "부가세", "봉사료", "할인", "포인트");

	/** 상호로 볼 수 없는 줄. 영수증 머리에는 상호 말고도 이런 줄이 섞인다. */
	private static final List<String> STORE_EXCLUSIONS = List.of(
			"사업자", "대표", "전화", "주소", "tel", "fax", "영수증", "주문", "가맹점");

	private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(?:,\\d{3})+|\\d+");
	/** 연도 4자리를 먼저 찾는다. 두 자리 연도는 사업자번호 같은 숫자와 헷갈리기 쉽다. */
	private static final Pattern FULL_YEAR_DATE = Pattern.compile("(19|20)(\\d{2})\\s*[-./]\\s*(\\d{1,2})\\s*[-./]\\s*(\\d{1,2})");
	private static final Pattern SHORT_YEAR_DATE = Pattern.compile("(?<!\\d)(\\d{2})\\s*[-./]\\s*(\\d{1,2})\\s*[-./]\\s*(\\d{1,2})(?!\\d)");
	private static final Pattern HANGUL = Pattern.compile("[가-힣]");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	/** 상호가 지나치게 길면 주소 줄을 잘못 집은 것이다. */
	private static final int MAX_STORE_NAME_LENGTH = 40;

	private ReceiptTextParser() {
	}

	static ReceiptOcrResult parse(List<String> lines) {
		Long totalAmount = findTotalAmount(lines);
		if (totalAmount == null) {
			throw new BusinessException(ErrorCode.OCR_RESULT_EMPTY);
		}
		// 품목은 뽑지 않는다. 범용 OCR 로 품목 행을 가려내려면 열 위치까지 추론해야 하는데,
		// 잘못 뽑은 품목은 없는 것보다 나쁘다(사용자가 하나씩 지워야 한다).
		return new ReceiptOcrResult(findStoreName(lines), findDate(lines), totalAmount, List.of());
	}

	/**
	 * 총액. 키워드가 걸린 줄에서 <b>가장 오른쪽 숫자</b>를 쓴다 — 영수증은 금액을 오른쪽 열에 맞춰 찍고,
	 * 줄 안의 조각은 가로 위치 순으로 이어 붙여 두었다.
	 */
	private static Long findTotalAmount(List<String> lines) {
		Long found = null;
		for (String line : lines) {
			String normalized = normalize(line);
			if (!containsAny(normalized, TOTAL_KEYWORDS) || containsAny(normalized, TOTAL_EXCLUSIONS)) {
				continue;
			}
			Long amount = lastNumberOf(line);
			if (amount != null) {
				found = amount;
			}
		}
		return found;
	}

	private static Long lastNumberOf(String line) {
		Matcher matcher = NUMBER.matcher(line);
		Long last = null;
		while (matcher.find()) {
			String digits = matcher.group().replace(",", "");
			if (digits.length() > 18) {
				continue;
			}
			long value = Long.parseLong(digits);
			if (value > 0) {
				last = value;
			}
		}
		return last;
	}

	/** 결제일. 위에서부터 처음 만나는 "말이 되는" 날짜를 쓴다. 못 찾으면 비운다(앱이 오늘로 채운다). */
	private static LocalDate findDate(List<String> lines) {
		for (String line : lines) {
			Matcher full = FULL_YEAR_DATE.matcher(line);
			while (full.find()) {
				LocalDate date = toDate(full.group(1) + full.group(2), full.group(3), full.group(4));
				if (date != null) {
					return date;
				}
			}
		}
		for (String line : lines) {
			Matcher shortYear = SHORT_YEAR_DATE.matcher(line);
			while (shortYear.find()) {
				LocalDate date = toDate("20" + shortYear.group(1), shortYear.group(2), shortYear.group(3));
				if (date != null) {
					return date;
				}
			}
		}
		return null;
	}

	private static LocalDate toDate(String year, String month, String day) {
		try {
			return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
		} catch (NumberFormatException | DateTimeException e) {
			// 사업자번호("123-45-67890")처럼 날짜 모양만 흉내 낸 숫자다.
			return null;
		}
	}

	/**
	 * 상호. 영수증 맨 위의 한글이 섞인 첫 줄을 쓴다 — 서식과 무관하게 상호가 먼저 오는 것은 거의 공통이다.
	 * 사업자번호·전화번호처럼 머리글에 함께 오는 줄은 걸러낸다.
	 */
	private static String findStoreName(List<String> lines) {
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.length() > MAX_STORE_NAME_LENGTH) {
				continue;
			}
			String normalized = normalize(trimmed).toLowerCase();
			if (containsAny(normalized, STORE_EXCLUSIONS) || !HANGUL.matcher(trimmed).find()) {
				continue;
			}
			return trimmed;
		}
		return null;
	}

	/** 키워드 비교용. "합 계"처럼 띄어 찍힌 것과 "합계"를 같게 본다. */
	private static String normalize(String line) {
		return WHITESPACE.matcher(line).replaceAll("");
	}

	private static boolean containsAny(String normalized, List<String> keywords) {
		return keywords.stream().anyMatch(normalized::contains);
	}
}
