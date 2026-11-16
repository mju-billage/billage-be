package com.billage.ocr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.billage.common.exception.BusinessException;

/**
 * 실제 영수증으로 인식 정확도를 잰다. <b>평소 빌드에서는 돌지 않는다</b> — {@code OCR_EVAL_DIR} 이 있을 때만.
 *
 * <pre>
 * OCR_EVAL_DIR=~/Project/billage-ocr-eval ./gradlew test --tests '*ReceiptOcrEvaluationTest'
 * </pre>
 *
 * <p>폴더에는 {@code tools/ocr-eval/record.py} 가 남긴 {@code *.fields.tsv}(CLOVA 응답의 글자·꼭짓점)와
 * 사람이 채운 {@code answers.csv} 가 있어야 한다. CLOVA 는 다시 부르지 않는다 — 규칙을 고칠 때마다
 * 같은 응답으로 다시 채점할 수 있어야 비교가 된다. 결과는 같은 폴더의 {@code report.md} 에 쓴다.
 *
 * <p>영수증 사진과 응답에는 카드번호 일부·가게 정보가 들어 있다. <b>저장소에 넣지 않는다</b>(공개 저장소다).
 *
 * <p>합격 기준을 단정하지 않고 숫자만 낸다. 다만 "품목을 채웠는데 틀림"은 0 이어야 한다 —
 * 그게 0 이 아니면 합계 검증이 뚫린 것이다.
 */
class ReceiptOcrEvaluationTest {

	private static final long SERVER_LIMIT_BYTES = 4L * 1024 * 1024;

	@Test
	@EnabledIfEnvironmentVariable(named = "OCR_EVAL_DIR", matches = ".+")
	void 저장된_영수증_응답으로_정확도를_잰다() throws IOException {
		Path dir = Path.of(System.getenv("OCR_EVAL_DIR").replaceFirst("^~", System.getProperty("user.home")));
		String report = evaluate(dir);
		Files.writeString(dir.resolve("report.md"), report, StandardCharsets.UTF_8);
		System.out.println(report);
	}

	static String evaluate(Path dir) throws IOException {
		List<Row> rows = new ArrayList<>();
		for (String line : Files.readAllLines(dir.resolve("answers.csv"), StandardCharsets.UTF_8).stream()
				.skip(1).toList()) {
			String[] cells = line.replace("﻿", "").split(",", 4);
			if (cells.length == 0 || cells[0].isBlank()) {
				continue;
			}
			rows.add(score(dir, cells[0].trim(), cell(cells, 1), cell(cells, 2), cell(cells, 3)));
		}
		return render(rows);
	}

	private static String cell(String[] cells, int index) {
		return index < cells.length ? cells[index].trim() : "";
	}

	private static Row score(Path dir, String file, String total, String itemAmounts, String memo) throws IOException {
		Long expectedTotal = total.isEmpty() ? null : Long.parseLong(total.replace(",", ""));
		List<Long> expectedItems = itemAmounts.isEmpty() ? null
				: Arrays.stream(itemAmounts.split("\\|")).map(String::trim).map(Long::parseLong).toList();
		String stem = file.replaceFirst("\\.[^.]+$", "");
		Path image = dir.resolve(file);
		long bytes = Files.exists(image) ? Files.size(image) : -1;

		Path fieldsFile = dir.resolve(stem + ".fields.tsv");
		if (!Files.exists(fieldsFile)) {
			return new Row(file, memo, bytes, expectedTotal, null, expectedItems, List.of(), List.of(), "응답 없음");
		}
		List<String> lines = ReceiptLineAssembler.assemble(fragments(fieldsFile));
		try {
			ReceiptOcrResult result = ReceiptTextParser.parse(lines);
			return new Row(file, memo, bytes, expectedTotal, result.totalAmount(), expectedItems, result.items(), lines,
					null);
		} catch (BusinessException e) {
			return new Row(file, memo, bytes, expectedTotal, null, expectedItems, List.of(), lines,
					e.getErrorCode().name());
		}
	}

	private static List<ReceiptLineAssembler.Fragment> fragments(Path fieldsFile) throws IOException {
		List<ReceiptLineAssembler.Fragment> fragments = new ArrayList<>();
		for (String line : Files.readAllLines(fieldsFile, StandardCharsets.UTF_8)) {
			String[] cells = line.split("\t");
			if (cells.length < 3) {
				continue;
			}
			List<ReceiptLineAssembler.Point> vertices = new ArrayList<>();
			for (int i = 1; i + 1 < cells.length; i += 2) {
				vertices.add(new ReceiptLineAssembler.Point(Double.parseDouble(cells[i]), Double.parseDouble(cells[i + 1])));
			}
			fragments.add(ReceiptLineAssembler.fragmentOf(cells[0], vertices));
		}
		return fragments;
	}

	private static String render(List<Row> rows) {
		StringBuilder out = new StringBuilder("# 영수증 인식 정확도\n\n");
		long answered = rows.stream().filter(row -> row.expectedTotal() != null).count();
		long totalCorrect = rows.stream().filter(Row::totalCorrect).count();
		long withItems = rows.stream().filter(row -> row.expectedItems() != null).count();
		long filled = rows.stream().filter(row -> row.expectedItems() != null && !row.items().isEmpty()).count();
		long itemsCorrect = rows.stream().filter(row -> "맞음".equals(row.itemVerdict())).count();
		long itemsWrong = rows.stream().filter(row -> "틀림".equals(row.itemVerdict())).count();
		long oversized = rows.stream().filter(row -> row.bytes() > SERVER_LIMIT_BYTES).count();

		out.append("| 항목 | 결과 |\n|---|---|\n")
				.append("| 총액 정확도 | %d / %d |\n".formatted(totalCorrect, answered))
				.append("| 품목 채움 | %d / %d |\n".formatted(filled, withItems))
				.append("| 품목 정확(금액 목록 일치) | %d / %d |\n".formatted(itemsCorrect, withItems))
				.append("| **품목을 채웠는데 틀림 (0 이어야 함)** | **%d** |\n".formatted(itemsWrong))
				.append("| 서버 상한 4MB 초과 사진 | %d / %d |\n\n".formatted(oversized, rows.size()));

		out.append("| 파일 | 크기 | 정답 총액 | 읽은 총액 | 총액 | 품목 | 읽은 품목 | 메모 |\n|---|---|---|---|---|---|---|---|\n");
		for (Row row : rows) {
			out.append("| %s | %s | %s | %s | %s | %s | %s | %s |\n".formatted(
					row.file(), row.bytes() < 0 ? "-" : "%.1fMB%s".formatted(row.bytes() / 1048576.0,
							row.bytes() > SERVER_LIMIT_BYTES ? " ⚠" : ""),
					Objects.toString(row.expectedTotal(), "-"),
					row.error() != null ? row.error() : Objects.toString(row.actualTotal(), "-"),
					row.expectedTotal() == null ? "-" : (row.totalCorrect() ? "✓" : "✗"),
					row.itemVerdict(),
					row.items().stream().map(item -> item.name() + " " + item.amount()).toList(),
					row.memo()));
		}

		out.append("\n## 틀렸거나 품목을 못 채운 영수증의 줄 복원 결과\n");
		for (Row row : rows) {
			boolean totalMissed = row.expectedTotal() != null && !row.totalCorrect();
			boolean itemsMissed = row.expectedItems() != null && !"맞음".equals(row.itemVerdict());
			if (totalMissed || itemsMissed) {
				out.append("\n### ").append(row.file()).append("\n```\n")
						.append(String.join("\n", row.lines())).append("\n```\n");
			}
		}
		return out.toString();
	}

	private record Row(String file, String memo, long bytes, Long expectedTotal, Long actualTotal,
					   List<Long> expectedItems, List<ReceiptOcrItem> items, List<String> lines, String error) {

		boolean totalCorrect() {
			return expectedTotal != null && expectedTotal.equals(actualTotal);
		}

		String itemVerdict() {
			if (expectedItems == null) {
				return "-";
			}
			if (items.isEmpty()) {
				return "비움";
			}
			return items.stream().map(ReceiptOcrItem::amount).toList().equals(expectedItems) ? "맞음" : "틀림";
		}
	}
}
