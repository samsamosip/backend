package com.youthbenefit.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 지원 금액 검사 (실데이터에서 나온 경우들) */
class AiSupportExtractorTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void keepsAmountWrittenInText() {
		Map<String, Object> r = check("1인당 월 30만원 최대 360만원 지원", 300000, "MONTH", "CASH", "1인당 월 30만원 최대 360만원 지원");
		assertThat(r.get("amountWon")).isEqualTo(300000L);
		assertThat(r.get("period")).isEqualTo("MONTH");
		assertThat(r.get("upTo")).isEqualTo(true);
	}

	@Test
	void usesTheWrittenNumberWhenAiMisreadsIt() {
		// #14 "연60백만원" 을 600만원으로 읽음 → 원문의 6,000만원
		Map<String, Object> r = check("장학급 지급(연60백만원)", 6000000, "YEAR", "CASH", "장학급 지급(연60백만원)");
		assertThat(r.get("amountWon")).isEqualTo(60000000L);
	}

	@Test
	void dropsAmountsThatAreNotWhatAPersonGets() {
		assertThat(check("총 예산 5억원, 1인당 교육 제공", 500000000, "", "CASH", "총 예산 5억원").get("amountWon")).isNull();
		assertThat(check("1개사당 40,000천원 지원", 40000000, "", "CASH", "1개사당 40,000천원 지원").get("amountWon")).isNull();
		assertThat(check("택배도난, 화재 보험 가입(최대 1억원)", 100000000, "", "CASH", "화재 보험 가입(최대 1억원)").get("amountWon"))
			.isNull();
		// 근거가 원문에 없음, 숫자가 근거에 없음
		assertThat(check("월세 지원", 200000, "MONTH", "CASH", "월 20만원 지원").get("amountWon")).isNull();
		// 교육·상담은 금액이 아님
		assertThat(check("1인당 20만원 상당 교육", 200000, "", "SERVICE", "1인당 20만원 상당 교육").get("amountWon")).isNull();
	}

	@Test
	void periodNeedsItsWordAndSummaryIsShort() {
		Map<String, Object> r = check("자립정착금 1,000만원 지급", 10000000, "MONTH", "CASH", "자립정착금 1,000만원 지급");
		assertThat(r.get("amountWon")).isEqualTo(10000000L);
		assertThat(r.get("period")).isNull();
		assertThat(r.get("summary")).isEqualTo("요약");
	}

	private static Map<String, Object> check(String source, long amount, String period, String kind, String evidence) {
		String raw = JSON.writeValueAsString(Map.of("amountWon", amount, "period", period, "upTo", false, "kind", kind,
				"evidence", evidence, "summary", "요약"));
		return AiSupportExtractor.check(JSON.readTree(raw), source, "m");
	}

}
