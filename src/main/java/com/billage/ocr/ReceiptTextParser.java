package com.billage.ocr;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

/**
 * 영수증 줄글에서 상호·결제일·총액·품목을 추려낸다. 범용 OCR 전용이다.
 *
 * <p><b>추측이 섞이는 코드다.</b> 영수증 서식이 가게마다 달라 규칙으로 100% 맞출 수 없다.
 * 그래서 확신이 서지 않으면 값을 지어내지 않고 비운다 — 총액만은 비울 수 없어(없으면 화면에 채울 게 없다)
 * 못 찾으면 {@code OCR_RESULT_EMPTY} 로 올린다.
 *
 * <p><b>이 클래스가 인식 품질을 결정한다.</b> 영수증 특화 모델을 쓰지 않기로 하면서(2026-09-21, 단가 문제)
 * 총액·결제일 판별이 통째로 여기 규칙에 달리게 됐다. 못 읽는 서식이 보고되면 키워드를 늘려 대응한다 —
 * 늘릴 때는 {@link #TOTAL_EXCLUSIONS} 와 부딪히지 않는지 함께 본다(구성 요소를 총액으로 집으면
 * 틀린 금액이 장부에 올라간다).
 *
 * <p>품목은 {@link ReceiptItemExtractor} 가 뽑는다. 품목 합이 영수증에 찍힌 합계와 맞을 때만 채운다.
 */
final class ReceiptTextParser {

	/**
	 * 총액 줄을 가리키는 말. 영수증은 보통 맨 아래쪽에 최종 금액을 한 번 더 찍으므로
	 * <b>마지막으로 걸린 줄</b>을 쓴다("합계" 뒤에 "결제금액"이 또 나오는 서식이 흔하다).
	 */
	static final List<String> TOTAL_KEYWORDS = List.of(
			"합계", "총액", "총계", "결제금액", "총결제금액", "받을금액", "청구금액", "판매금액", "총구매액");

	/**
	 * 위 키워드가 하나도 없을 때만 보는 줄. 택시 영수증은 "요 금: 10,900원" 한 줄이 총액이다.
	 * <b>"요금"이 줄 맨 앞에 혼자 올 때만</b> 본다 — "주차요금 2시간 6,000"·"기본요금 3,800"은 개별 요금이라,
	 * 합계 줄이 가려진 영수증에서 이걸 집으면 못 읽었다고 하는 대신 틀린 총액을 채우게 된다.
	 */
	private static final Pattern FARE_LINE = Pattern.compile("^요금[:：]?[\\d₩\\\\]");

	/** 총액처럼 보이지만 구성 요소인 줄. 이 말이 있으면 총액 줄로 보지 않는다. */
	private static final List<String> TOTAL_EXCLUSIONS = List.of(
			"과세", "면세", "부가세", "봉사료", "할인", "포인트");

	/**
	 * 총액 줄에 함께 찍히는 개수("총 수량 3", "건수: 2개"). 지운 뒤에 총액 줄인지 보고 금액을 읽는다 —
	 * "총 수량 합계 3"은 남는 것이 없어 총액 줄이 아니고(그대로 두면 3원이 총액이 된다),
	 * "총수량 3 합계 15,000"은 "합계 15,000"이 남아 총액 줄이다.
	 * 이 모양으로 못 지운 개수 표기("합계수량(EA) 3")가 남으면 {@link #QUANTITY_WORDS} 로 줄을 통째로 버린다.
	 */
	private static final Pattern QUANTITY_PART = Pattern.compile(
			"(?:총\\s*)?(?:수\\s*량|건\\s*수)(?:\\s*합\\s*계)?\\s*[:：]?\\s*\\d+\\s*(?:개|건|EA|ea)?");

	/** {@link #QUANTITY_PART} 로 지우고도 남아 있으면 개수를 세는 줄이다. 금액이 아니라 개수를 총액으로 집지 않게 버린다. */
	private static final List<String> QUANTITY_WORDS = List.of("수량", "건수");

	/**
	 * "부가세포함 합계"처럼 제외어가 <b>총액을 꾸미는</b> 경우. 이 말을 지운 뒤에 제외어를 본다 —
	 * 그대로 보면 "부가세"에 걸려 진짜 총액 줄을 버린다.
	 */
	private static final Pattern TAX_INCLUDED = Pattern.compile("(부가세|부가가치세|세금|VAT|vat)포함");

	/** 합계 앞에 찍히는 중간 합계. 총액 후보는 아니지만 품목 합을 맞춰 볼 대상이다(할인 전 금액). */
	private static final String SUBTOTAL_KEYWORD = "소계";

	/** 상호로 볼 수 없는 줄. 영수증 머리에는 상호 말고도 이런 줄이 섞인다. */
	private static final List<String> STORE_EXCLUSIONS = List.of(
			"사업자", "대표", "전화", "주소", "tel", "fax", "영수증", "주문", "가맹점");

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
		List<Long> printedTotals = printedTotals(lines);
		if (!printedTotals.contains(totalAmount)) {
			printedTotals.add(totalAmount);
		}
		List<ReceiptOcrItem> items = ReceiptItemExtractor.extract(lines, printedTotals, totalAmount);
		return new ReceiptOcrResult(findStoreName(lines), findDate(lines), totalAmount, items);
	}

	/**
	 * 총액. 키워드가 걸린 줄에서 <b>가장 오른쪽 금액</b>을 쓴다 — 영수증은 금액을 오른쪽 열에 맞춰 찍고,
	 * 줄 안의 조각은 가로 위치 순으로 이어 붙여 두었다. 숫자 오인식 보정과 번호(승인번호 등) 거르기는
	 * {@link ReceiptAmounts} 가 한다.
	 */
	private static Long findTotalAmount(List<String> lines) {
		// 총액 줄이 있는데 금액만 못 읽었으면(가려짐) 요금 줄로 넘어가지 않는다 — 틀린 총액보다 못 읽음이 낫다.
		if (lines.stream().anyMatch(ReceiptTextParser::isTotalLine)) {
			return lastAmountOfLines(lines, ReceiptTextParser::isTotalLine);
		}
		return lastAmountOfLines(lines, line -> FARE_LINE.matcher(normalize(line)).find());
	}

	private static Long lastAmountOfLines(List<String> lines, Predicate<String> isTarget) {
		Long found = null;
		for (String line : lines) {
			if (!isTarget.test(line)) {
				continue;
			}
			Long amount = ReceiptAmounts.lastAmountOf(withoutQuantity(line));
			if (amount != null) {
				found = amount;
			}
		}
		return found;
	}

	private static boolean isTotalLine(String line) {
		String normalized = normalize(withoutQuantity(line));
		if (!containsAny(normalized, TOTAL_KEYWORDS)) {
			return false;
		}
		return !containsAny(TAX_INCLUDED.matcher(normalized).replaceAll(""), TOTAL_EXCLUSIONS)
				&& !containsAny(normalized, QUANTITY_WORDS);
	}

	private static String withoutQuantity(String line) {
		return QUANTITY_PART.matcher(line).replaceAll(" ");
	}

	/**
	 * 영수증에 찍힌 합계들(총액 후보 전부와 소계). 품목 합이 이 중 하나와 맞아야 품목을 믿는다 —
	 * 할인이 있으면 품목 합은 최종 결제금액이 아니라 할인 전 합계와 맞는다.
	 */
	private static List<Long> printedTotals(List<String> lines) {
		List<Long> totals = new ArrayList<>();
		for (String line : lines) {
			boolean subtotal = normalize(line).contains(SUBTOTAL_KEYWORD);
			if (!isTotalLine(line) && !subtotal) {
				continue;
			}
			Long amount = ReceiptAmounts.lastAmountOf(withoutQuantity(line));
			if (amount != null) {
				totals.add(amount);
			}
		}
		return totals;
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
	static String normalize(String line) {
		return WHITESPACE.matcher(line).replaceAll("");
	}

	static boolean containsAny(String normalized, List<String> keywords) {
		return keywords.stream().anyMatch(normalized::contains);
	}
}
