package com.billage.ocr;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 외부 OCR 을 호출하지 않고 고정된 결과를 돌려준다. 로컬 개발과 테스트에서 클로바 과금·키 없이
 * 인식 흐름을 확인하는 용도다.
 *
 * <p>결과를 무작위로 만들지 않는다 — 프론트가 화면을 붙이는 동안 같은 입력에 같은 값이 나와야
 * 무엇이 반영됐는지 볼 수 있다. 인식 실패(422) 흐름은 이 구현으로 재현하지 않는다.
 *
 * <p>이 구현이 실서버에서 선택되면 <b>사용자가 지어낸 금액을 진짜로 믿고 장부에 올리는데 오류도 나지 않는다</b> —
 * 재무 데이터라 조용히 틀리는 쪽이 가장 나쁘다. 그래서 배포 환경에서는 뜨지 못하게 막는다
 * ({@code LogMailSender} 와 같은 방식이되, prod 뿐 아니라 dev 도 실패시킨다 — dev 는 프론트가
 * 실제 인식을 확인하는 환경이라 가짜 금액이 섞이면 검증 자체가 무의미해진다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "billage.ocr.provider", havingValue = "STUB", matchIfMissing = true)
public class StubReceiptOcrClient implements ReceiptOcrClient {

	/** 실제 사용자가 없는 환경. 활성 프로필이 비어 있는 로컬을 오인하지 않도록 {@code acceptsProfiles} 로 판정한다. */
	private static final Profiles OFFLINE = Profiles.of("local", "test");

	private static final String STUB_MERCHANT_NAME = "스텁 상점";
	private static final long STUB_TOTAL_AMOUNT = 13_000L;

	private final Environment environment;

	@PostConstruct
	void guardAgainstSilentDeployment() {
		if (!environment.acceptsProfiles(OFFLINE)) {
			throw new IllegalStateException(
					"배포 환경에서 영수증 인식이 스텁으로 동작합니다. billage.ocr.provider=CLOVA 로 설정하세요. "
							+ "activeProfiles=" + List.of(environment.getActiveProfiles()));
		}
	}

	@Override
	public ReceiptOcrResult recognize(byte[] image, ReceiptImageFormat format) {
		log.info("[OCR 호출 생략 - STUB 모드] format={} bytes={}", format.value(), image.length);
		return new ReceiptOcrResult(STUB_MERCHANT_NAME, LocalDate.now(), STUB_TOTAL_AMOUNT, List.of(
				new ReceiptOcrItem("생수", 2, 1_000L, 2_000L, 0.96),
				new ReceiptOcrItem("과자", 1, 11_000L, 11_000L, 0.88)));
	}
}
