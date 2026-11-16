package com.billage.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 기울어진 사진에서 줄을 되돌리는지 검증.
 *
 * <p>지어낸 좌표가 아니라 <b>실제 CLOVA 범용 OCR 응답</b>({@code clova-general-receipt-response.json})의 꼭짓점을
 * 회전시켜 쓴다. 손으로 찍은 영수증은 몇 도씩 기울기 마련이고, 3° 만 기울어도 "합계"의 금액이 위 줄
 * (과세물품가액)에 붙어 5,909원이 총액으로 나왔다.
 */
class ReceiptLineAssemblerTest {

	private static final Path FIXTURE = Path.of("src/test/resources/ocr/clova-general-receipt-response.json");
	private static final Pattern FIELD = Pattern.compile(
			"\"inferText\":\"(.*?)\",\"boundingPoly\":\\{\"vertices\":\\[(.*?)]}");
	private static final Pattern VERTEX = Pattern.compile("\"x\":(-?[\\d.]+),\"y\":(-?[\\d.]+)");

	@Test
	void 기울지_않은_영수증은_그대로_읽는다() throws IOException {
		assertReadsFixture(0);
	}

	@Test
	void 시계방향으로_3도_기운_영수증을_읽는다() throws IOException {
		assertReadsFixture(3);
	}

	@Test
	void 반시계방향으로_5도_기운_영수증을_읽는다() throws IOException {
		assertReadsFixture(-5);
	}

	@Test
	void 크게_8도_기운_영수증도_읽는다() throws IOException {
		assertReadsFixture(8);
	}

	private void assertReadsFixture(double degrees) throws IOException {
		List<String> lines = ReceiptLineAssembler.assemble(rotatedFixture(degrees));

		assertThat(lines).contains("합계 6,500", "생수 500ml 2 1,000 2,000");
		ReceiptOcrResult result = ReceiptTextParser.parse(lines);
		assertThat(result.totalAmount()).isEqualTo(6_500L);
		assertThat(result.items()).extracting(ReceiptOcrItem::name)
				.containsExactly("생수 500ml", "초코과자", "종이컵 50개");
	}

	/** 영수증 왼쪽 위를 축으로 꼭짓점을 돌린다. 양수가 시계 방향(화면 좌표는 y 가 아래로 자란다). */
	private static List<ReceiptLineAssembler.Fragment> rotatedFixture(double degrees) throws IOException {
		double radians = Math.toRadians(degrees);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);

		List<ReceiptLineAssembler.Fragment> fragments = new ArrayList<>();
		Matcher field = FIELD.matcher(Files.readString(FIXTURE));
		while (field.find()) {
			List<ReceiptLineAssembler.Point> vertices = new ArrayList<>();
			Matcher vertex = VERTEX.matcher(field.group(2));
			while (vertex.find()) {
				double x = Double.parseDouble(vertex.group(1));
				double y = Double.parseDouble(vertex.group(2));
				vertices.add(new ReceiptLineAssembler.Point(x * cos - y * sin, x * sin + y * cos));
			}
			fragments.add(ReceiptLineAssembler.fragmentOf(field.group(1), vertices));
		}
		assertThat(fragments).hasSize(38);
		return fragments;
	}
}
