package com.billage.ocr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 영수증 줄글에서 <b>품목</b>(이름·수량·단가·금액)을 뽑는다. 범용 OCR 전용이다.
 *
 * <p><b>"잘못 뽑은 품목은 없는 것보다 나쁘다"는 원칙은 그대로다.</b> 그래서 규칙이 맞힌 것 같아도 바로 믿지 않고,
 * <b>품목 금액의 합이 영수증에 찍힌 합계와 맞을 때만</b> 품목을 돌려준다. 하나라도 잘못 읽었거나 빠뜨렸으면
 * 합이 어긋나므로 통째로 빈 목록이 된다 — 사용자는 지금처럼 직접 채우면 되고, 틀린 품목을 지울 일은 없다.
 *
 * <p>품목이 어디 있는지는 이렇게 찾는다. 영수증은 서식이 달라도 "품목들 → 합계 묶음(과세·부가세·합계)" 순서는
 * 같다. 그래서 <b>합계 묶음이 시작되는 줄 바로 위에서부터 위로</b> 품목처럼 생긴 줄을 모으고, 품목이 아닌 줄
 * (머리글·날짜·사업자번호)을 만나면 멈춘다.
 *
 * <p>한 줄은 "이름 [수량] [단가] 금액" 꼴이다. 수량·단가는 영수증마다 순서가 달라서({@code 수량 단가 금액} /
 * {@code 단가 수량 금액}) 곱이 금액과 맞는지로 확인하고, 둘 중 작은 쪽을 수량으로 본다. 맞지 않으면 비운다.
 * 이름이 한 줄, 숫자가 다음 줄에 찍히는 서식(바코드가 함께 찍히는 마트 영수증)도 받는다.
 */
final class ReceiptItemExtractor {

	/** 합계 묶음이 시작됐다고 볼 말. 할인·포인트는 품목 사이에도 끼므로 넣지 않는다. */
	private static final List<String> SUMMARY_KEYWORDS = Stream.concat(
			ReceiptTextParser.TOTAL_KEYWORDS.stream(),
			Stream.of("과세", "면세", "부가세", "봉사료", "공급가", "물품가액", "소계", "수량", "건수")).toList();

	/** 품목표 머리글("상품명 단가 수량 금액")에 쓰이는 말. 두 개 이상 있고 숫자가 없으면 머리글이다. */
	private static final List<String> HEADER_WORDS = List.of("상품명", "품명", "품목", "메뉴", "단가", "수량", "금액");
	private static final int HEADER_MIN_WORDS = 2;

	/** 품목 사이에 끼는 할인 줄. 품목이 아니라 합에서 빼는 금액이다. */
	private static final List<String> DISCOUNT_WORDS = List.of("할인", "쿠폰", "에누리");

	/** 한 줄 끝에서 읽을 숫자 개수 상한(수량·단가·할인·금액). */
	private static final int MAX_TRAILING_NUMBERS = 4;
	/** 이보다 크면 수량이 아니다. */
	private static final long MAX_QUANTITY = 999;

	/** 이름 앞에 붙는 품번(001)·바코드. 이름이 아니다. */
	private static final Pattern ITEM_CODE = Pattern.compile("^(?:\\d{1,4}|\\d{8,})$");
	/** 이름 앞에 붙는 표시(면세 품목의 "*", "#" 등). */
	private static final Pattern NAME_MARKER = Pattern.compile("^[*#\\[\\]()]+");
	/** 금액 뒤에 붙는 같은 표시("4,280*", "3,960*#", "3,960 #"). 떼고 읽는다. */
	private static final Pattern AMOUNT_MARKER = Pattern.compile("[*#]+$");

	/** 합계 묶음 바로 위에 끼는 소제목("[결제 내역]") 중 건너뛸 줄 수. 그 위로는 품목표여야 한다. */
	private static final int MAX_SKIPPED_TITLES = 1;
	private static final Pattern LETTER = Pattern.compile("\\p{L}");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private ReceiptItemExtractor() {
	}

	/**
	 * @param printedTotals 영수증에 찍힌 합계들(총액 후보와 소계). 품목 합이 이 중 하나와 맞아야 한다.
	 * @return 위에서 아래 순서의 품목. 확신이 없으면 빈 목록.
	 */
	static List<ReceiptOcrItem> extract(List<String> lines, List<Long> printedTotals) {
		int summaryStart = findSummaryStart(lines);
		if (summaryStart <= 0 || printedTotals.isEmpty()) {
			return List.of();
		}

		List<ReceiptOcrItem> items = new ArrayList<>();
		long adjustments = 0;
		List<Long> pendingNumbers = null;
		int skippedTitles = 0;

		for (int i = summaryStart - 1; i >= 0; i--) {
			Row row = Row.of(lines.get(i));

			if (row.isBlankOrSeparator()) {
				// 품목과 합계 사이의 구분선("------")은 건너뛴다. 품목을 모으던 중이면 품목표가 끝난 것이다.
				if (items.isEmpty() && pendingNumbers == null && adjustments == 0) {
					continue;
				}
				break;
			}
			if (row.isHeader()) {
				break;
			}

			if (row.numbers().isEmpty()) {
				if (items.isEmpty() && pendingNumbers == null && skippedTitles < MAX_SKIPPED_TITLES) {
					// 아직 품목을 하나도 못 만났으면 합계 묶음 위의 소제목이다.
					skippedTitles++;
					continue;
				}
				// 이름만 있는 줄 — 바로 아래 줄의 숫자와 짝이다. 짝이 없으면 품목표 위의 다른 내용이다.
				if (pendingNumbers == null || !row.hasName()) {
					break;
				}
				addItem(items, row.name(), pendingNumbers);
				pendingNumbers = null;
				continue;
			}

			if (pendingNumbers != null) {
				// 숫자 줄 위에 이름 없이 또 숫자 줄이 오면 줄을 짝지을 수 없다.
				break;
			}
			if (!row.hasName()) {
				pendingNumbers = row.numbers();
				continue;
			}

			long amount = row.numbers().getLast();
			if (row.isDiscount() || amount < 0) {
				adjustments -= Math.abs(amount);
				continue;
			}
			addItem(items, row.name(), row.numbers());
		}

		if (items.isEmpty() || !matchesPrintedTotal(items, adjustments, printedTotals)) {
			return List.of();
		}
		return items.reversed();
	}

	/**
	 * 합계 묶음이 시작되는 줄. 위에서부터 처음 걸리는 줄이다 — 영수증은 "합계"보다 "과세물품가액"이
	 * 먼저 찍히기도 해서 마지막 총액 줄이 아니라 묶음의 첫 줄을 찾아야 한다. 머리글의 "수량"은 건너뛴다.
	 * 품목표 머리글이 있으면 <b>그 아래에서만</b> 찾는다 — 맨 위 안내문("면세상품 일부는 적립 제외")의
	 * "면세"를 합계 묶음으로 보면 품목을 하나도 못 읽는다.
	 */
	private static int findSummaryStart(List<String> lines) {
		int from = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (Row.of(lines.get(i)).isHeader()) {
				from = i + 1;
				break;
			}
		}
		for (int i = from; i < lines.size(); i++) {
			Row row = Row.of(lines.get(i));
			if (!row.isHeader() && ReceiptTextParser.containsAny(row.compact(), SUMMARY_KEYWORDS)) {
				return i;
			}
		}
		return -1;
	}

	private static void addItem(List<ReceiptOcrItem> items, String name, List<Long> numbers) {
		long amount = numbers.getLast();
		if (amount <= 0) {
			// 0원 줄(증정품 등)은 합에 영향이 없고, 화면에 "0원짜리 품목"으로 보일 뿐이다.
			return;
		}
		Integer quantity = null;
		Long unitPrice = null;
		if (numbers.size() >= 3) {
			// "수량 단가 금액" 또는 "단가 수량 금액". 곱이 금액과 맞을 때만 믿는다 —
			// 중간에 할인 열이 끼는 서식에서 아무 숫자나 나눠 수량을 지어내지 않도록.
			long first = numbers.get(numbers.size() - 3);
			long second = numbers.get(numbers.size() - 2);
			if (first > 0 && second > 0 && first * second == amount) {
				quantity = asQuantity(Math.min(first, second));
				unitPrice = quantity == null ? null : Math.max(first, second);
			}
		} else if (numbers.size() == 2) {
			// "수량 금액" 또는 "단가 금액". 나머지 하나는 나눠서 구한다.
			long first = numbers.getFirst();
			if (first > 0 && amount % first == 0) {
				long other = amount / first;
				quantity = asQuantity(Math.min(first, other));
				unitPrice = quantity == null ? null : Math.max(first, other);
			}
		}
		items.add(new ReceiptOcrItem(name, quantity, unitPrice, amount, null));
	}

	private static Integer asQuantity(long value) {
		return value > 0 && value <= MAX_QUANTITY ? (int) value : null;
	}

	/**
	 * 품목 합이 영수증에 찍힌 합계와 맞는지. 할인 줄을 뺀 합(최종 금액)과 빼기 전 합(할인 전 합계)
	 * 둘 다 영수증에 찍힐 수 있는 값이라 어느 쪽이든 맞으면 믿는다.
	 */
	private static boolean matchesPrintedTotal(List<ReceiptOcrItem> items, long adjustments, List<Long> printedTotals) {
		long sum = items.stream().mapToLong(ReceiptOcrItem::amount).sum();
		return printedTotals.contains(sum) || printedTotals.contains(sum + adjustments);
	}

	/** 줄 하나를 "이름 + 끝에 붙은 숫자들"로 나눈 것. */
	private record Row(String name, List<Long> numbers, String compact) {

		static Row of(String line) {
			String normalized = ReceiptAmounts.normalize(line).trim();
			String[] tokens = normalized.isEmpty() ? new String[0] : WHITESPACE.split(normalized);

			int nameEnd = tokens.length;
			List<Long> numbers = new ArrayList<>();
			while (nameEnd > 0 && numbers.size() < MAX_TRAILING_NUMBERS) {
				String token = AMOUNT_MARKER.matcher(tokens[nameEnd - 1]).replaceFirst("");
				if (token.isEmpty()) {
					nameEnd--;
					continue;
				}
				Long value = ReceiptAmounts.parseToken(token);
				if (value == null) {
					break;
				}
				numbers.addFirst(value);
				nameEnd--;
			}

			int nameStart = 0;
			while (nameStart < nameEnd && ITEM_CODE.matcher(tokens[nameStart]).matches()) {
				nameStart++;
			}
			String name = String.join(" ", List.of(tokens).subList(nameStart, nameEnd));
			name = NAME_MARKER.matcher(name).replaceFirst("").trim();
			return new Row(name, numbers, ReceiptTextParser.normalize(line));
		}

		boolean hasName() {
			return LETTER.matcher(name).find();
		}

		boolean isBlankOrSeparator() {
			return compact.chars().noneMatch(Character::isLetterOrDigit);
		}

		boolean isHeader() {
			return numbers.isEmpty()
					&& HEADER_WORDS.stream().filter(compact::contains).count() >= HEADER_MIN_WORDS;
		}

		boolean isDiscount() {
			return ReceiptTextParser.containsAny(compact, DISCOUNT_WORDS);
		}
	}
}
