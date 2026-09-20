package com.billage.ocr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 범용 OCR 이 돌려준 글자 조각을 영수증의 <b>줄</b>로 되돌린다.
 *
 * <p>클로바 범용 모델은 조각마다 {@code lineBreak} 를 주지만 그대로 믿을 수 없다 — 실제 응답에서
 * 영수증의 "합 계"와 "6,500" 이 서로 다른 {@code lineBreak} 묶음으로 떨어져 나왔다(금액 열을
 * 통째로 따로 읽는다). 그래서 {@code boundingPoly} 의 세로 위치로 직접 묶는다.
 *
 * <p>판정은 "세로 중심이 글자 높이의 절반 안쪽이면 같은 줄"이다. 영수증은 줄 간격이 글자 높이와
 * 비슷해서 이보다 느슨하면 위아래 줄이 붙고, 빡빡하면 글씨 크기가 다른 열이 갈라진다.
 */
final class ReceiptLineAssembler {

	/** 같은 줄로 볼 세로 중심 차이(글자 높이 대비). */
	private static final double SAME_LINE_RATIO = 0.5;

	private ReceiptLineAssembler() {
	}

	/** 조각 하나. 가로 위치로 줄 안에서 정렬하고, 세로 중심·높이로 줄을 나눈다. */
	record Fragment(String text, double x, double centerY, double height) {
	}

	static List<String> assemble(List<Fragment> fragments) {
		List<List<Fragment>> rows = new ArrayList<>();
		List<Fragment> ordered = new ArrayList<>(fragments);
		// 위에서 아래로 훑어야 한 줄이 두 번 열리지 않는다.
		ordered.sort(Comparator.comparingDouble(Fragment::centerY).thenComparingDouble(Fragment::x));

		for (Fragment fragment : ordered) {
			List<Fragment> row = findRow(rows, fragment);
			if (row == null) {
				rows.add(new ArrayList<>(List.of(fragment)));
			} else {
				row.add(fragment);
			}
		}

		return rows.stream().map(ReceiptLineAssembler::toLine).toList();
	}

	private static List<Fragment> findRow(List<List<Fragment>> rows, Fragment fragment) {
		for (List<Fragment> row : rows) {
			double rowCenterY = row.stream().mapToDouble(Fragment::centerY).average().orElse(0);
			double height = Math.max(fragment.height(), row.stream().mapToDouble(Fragment::height).max().orElse(0));
			if (Math.abs(rowCenterY - fragment.centerY()) <= height * SAME_LINE_RATIO) {
				return row;
			}
		}
		return null;
	}

	private static String toLine(List<Fragment> row) {
		return row.stream()
				.sorted(Comparator.comparingDouble(Fragment::x))
				.map(Fragment::text)
				.reduce((left, right) -> left + " " + right)
				.orElse("");
	}
}
