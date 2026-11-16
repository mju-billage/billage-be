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
 *
 * <p><b>사진이 기울어 있으면 먼저 바로 세운다.</b> 손으로 찍은 영수증은 몇 도씩 기울기 마련인데,
 * 폭이 500px 인 영수증이 3° 만 기울어도 오른쪽 금액 열이 왼쪽 글자보다 26px 내려간다 — 글자 높이의 절반을 넘어
 * "합계"와 그 금액이 다른 줄로 갈라지고, 금액이 위 줄(과세물품가액)에 붙어 <b>틀린 총액</b>이 나왔다.
 * 그래서 {@link #estimateSlope} 로 기울기를 찾아 세로 위치를 보정한 뒤에 묶는다.
 */
final class ReceiptLineAssembler {

	/** 같은 줄로 볼 세로 중심 차이(글자 높이 대비). */
	private static final double SAME_LINE_RATIO = 0.5;

	/** 기울기를 찾아볼 범위(도). 이보다 많이 기운 사진은 다시 찍게 하는 편이 낫다. */
	private static final double MAX_SKEW_DEGREES = 10.0;
	private static final double SKEW_STEP_DEGREES = 0.25;
	/** 기울기를 셀 때 "같은 줄"로 볼 차이(글자 높이 대비). 줄 묶기보다 빡빡해야 각도가 날카롭게 갈린다. */
	private static final double ALIGNED_RATIO = 0.2;

	private ReceiptLineAssembler() {
	}

	/**
	 * 조각 하나. {@code x} 는 왼쪽 끝으로 줄 안의 순서를 정하고, 기울기 보정은 가운데({@code centerX})를 기준으로 한다 —
	 * 긴 조각을 왼쪽 끝으로 보정하면 폭의 절반만큼 어긋난다.
	 */
	record Fragment(String text, double x, double centerX, double centerY, double height) {

		/** 좌표가 축에 맞춰진 조각. 가운데를 따로 모르면 왼쪽 끝으로 대신한다. */
		Fragment(String text, double x, double centerY, double height) {
			this(text, x, x, centerY, height);
		}

		private double leveledY(double slope) {
			return centerY - slope * centerX;
		}
	}

	/** OCR 이 준 꼭짓점 하나. */
	record Point(double x, double y) {
	}

	/**
	 * 꼭짓점(좌상·우상·우하·좌하 순)에서 조각을 만든다. 꼭짓점이 없으면 null — 어느 줄인지 알 수 없는 조각을
	 * 아무 줄에나 끼워 넣으면 총액 줄을 망가뜨린다.
	 *
	 * <p>사진이 기울면 사각형도 같이 기운다. 그때 세로 폭(maxY−minY)을 글자 높이로 쓰면 긴 조각일수록
	 * 높이가 부풀어 위아래 줄이 붙으므로, 꼭짓점이 넷이면 좌우 변의 길이를 높이로 쓴다.
	 */
	static Fragment fragmentOf(String text, List<Point> vertices) {
		if (vertices == null || vertices.isEmpty()) {
			return null;
		}
		double minX = vertices.stream().mapToDouble(Point::x).min().orElse(0);
		double centerX = vertices.stream().mapToDouble(Point::x).average().orElse(0);
		double centerY = vertices.stream().mapToDouble(Point::y).average().orElse(0);
		double height;
		if (vertices.size() == 4) {
			height = (distance(vertices.get(0), vertices.get(3)) + distance(vertices.get(1), vertices.get(2))) / 2;
		} else {
			height = vertices.stream().mapToDouble(Point::y).max().orElse(0)
					- vertices.stream().mapToDouble(Point::y).min().orElse(0);
		}
		return new Fragment(text, minX, centerX, centerY, height);
	}

	private static double distance(Point a, Point b) {
		return Math.hypot(a.x() - b.x(), a.y() - b.y());
	}

	static List<String> assemble(List<Fragment> fragments) {
		double slope = estimateSlope(fragments);
		List<Leveled> ordered = new ArrayList<>(fragments.stream()
				.map(fragment -> new Leveled(fragment, fragment.leveledY(slope)))
				.toList());
		// 위에서 아래로 훑어야 한 줄이 두 번 열리지 않는다.
		ordered.sort(Comparator.comparingDouble(Leveled::y).thenComparingDouble(leveled -> leveled.fragment().x()));

		List<List<Leveled>> rows = new ArrayList<>();
		for (Leveled fragment : ordered) {
			List<Leveled> row = findRow(rows, fragment);
			if (row == null) {
				rows.add(new ArrayList<>(List.of(fragment)));
			} else {
				row.add(fragment);
			}
		}

		return rows.stream().map(ReceiptLineAssembler::toLine).toList();
	}

	/**
	 * 사진의 기울기(tan). 후보 각도마다 "가로로 떨어져 있으면서 같은 높이에 있는 조각 쌍"을 세어
	 * 가장 많이 맞는 각도를 고른다 — 영수증은 줄마다 왼쪽 글자와 오른쪽 금액이 짝을 이루므로, 바른 각도에서만
	 * 그 짝들이 한꺼번에 맞아떨어진다. 같은 점수면 0° 에 가까운 쪽을 고른다(바로 찍은 사진은 건드리지 않는다).
	 */
	static double estimateSlope(List<Fragment> fragments) {
		if (fragments.size() < 2) {
			return 0;
		}
		double bestSlope = 0;
		long bestScore = alignedPairs(fragments, 0);
		for (double degrees = -MAX_SKEW_DEGREES; degrees <= MAX_SKEW_DEGREES; degrees += SKEW_STEP_DEGREES) {
			double slope = Math.tan(Math.toRadians(degrees));
			long score = alignedPairs(fragments, slope);
			if (score > bestScore || (score == bestScore && Math.abs(slope) < Math.abs(bestSlope))) {
				bestScore = score;
				bestSlope = slope;
			}
		}
		return bestSlope;
	}

	private static long alignedPairs(List<Fragment> fragments, double slope) {
		long count = 0;
		for (int i = 0; i < fragments.size(); i++) {
			Fragment a = fragments.get(i);
			for (int j = i + 1; j < fragments.size(); j++) {
				Fragment b = fragments.get(j);
				double height = Math.max(a.height(), b.height());
				// 위아래로 붙은 조각은 각도와 상관없이 맞아 보인다. 옆으로 떨어진 쌍만 센다.
				if (Math.abs(a.centerX() - b.centerX()) < height) {
					continue;
				}
				if (Math.abs(a.leveledY(slope) - b.leveledY(slope)) <= height * ALIGNED_RATIO) {
					count++;
				}
			}
		}
		return count;
	}

	private static List<Leveled> findRow(List<List<Leveled>> rows, Leveled fragment) {
		for (List<Leveled> row : rows) {
			double rowCenterY = row.stream().mapToDouble(Leveled::y).average().orElse(0);
			double height = Math.max(fragment.fragment().height(),
					row.stream().mapToDouble(leveled -> leveled.fragment().height()).max().orElse(0));
			if (Math.abs(rowCenterY - fragment.y()) <= height * SAME_LINE_RATIO) {
				return row;
			}
		}
		return null;
	}

	private static String toLine(List<Leveled> row) {
		return row.stream()
				.map(Leveled::fragment)
				.sorted(Comparator.comparingDouble(Fragment::x))
				.map(Fragment::text)
				.reduce((left, right) -> left + " " + right)
				.orElse("");
	}

	/** 기울기를 보정한 세로 위치를 붙여 둔 조각. */
	private record Leveled(Fragment fragment, double y) {
	}
}
