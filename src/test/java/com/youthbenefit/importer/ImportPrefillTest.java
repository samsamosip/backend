package com.youthbenefit.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ImportPrefillTest {

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void goodResultIsKeptAsIs() {
		ImportPrefill p = ImportPrefill.from(json.readTree("""
				{"title": " OO구 청년 문화패스 ", "organization": "OO구청", "applyEndDate": "2026-11-20",
				 "applyEndTime": "18:00", "category": "복지문화", "memo": "연 20만원", "sourceUrl": "https://example.go.kr/n/1"}
				"""), "https://example.go.kr/n/1?ref=share");
		assertThat(p).isEqualTo(new ImportPrefill("OO구 청년 문화패스", "OO구청", LocalDate.of(2026, 11, 20),
				LocalTime.of(18, 0), "복지문화", "연 20만원", "https://example.go.kr/n/1"));
	}

	@Test
	void fieldsOutsideRulesBecomeNull() {
		ImportPrefill p = ImportPrefill.from(json.readTree("""
				{"title": "", "organization": 3, "applyEndDate": "2026/11/20", "applyEndTime": "18:00",
				 "category": "장학", "memo": null, "sourceUrl": "javascript:alert(1)"}
				"""), "https://example.go.kr/n/1");
		assertThat(p.title()).isNull();
		assertThat(p.organization()).isNull();
		assertThat(p.applyEndDate()).isNull();
		// 마감일 없이 시각만 있으면 등록할 때 오류라 뺀다
		assertThat(p.applyEndTime()).isNull();
		assertThat(p.category()).isNull();
		assertThat(p.memo()).isNull();
		// 원문 링크가 이상하면 사용자가 준 링크
		assertThat(p.applyUrl()).isEqualTo("https://example.go.kr/n/1");
	}

	@Test
	void impossibleDatesAndTimesBecomeNull() {
		ImportPrefill p = ImportPrefill.from(json.readTree("""
				{"applyEndDate": "2026-02-30", "applyEndTime": "25:00"}
				"""), null);
		assertThat(p.applyEndDate()).isNull();
		ImportPrefill q = ImportPrefill.from(json.readTree("""
				{"applyEndDate": "2026-02-28", "applyEndTime": "9:00"}
				"""), null);
		assertThat(q.applyEndDate()).isEqualTo(LocalDate.of(2026, 2, 28));
		assertThat(q.applyEndTime()).isNull();
		assertThat(q.applyUrl()).isNull();
	}

	@Test
	void longTextIsCutToInputLimits() {
		ImportPrefill p = ImportPrefill.from(json.readTree("""
				{"title": "%s", "memo": "%s"}
				""".formatted("가".repeat(300), "나".repeat(3000))), null);
		assertThat(p.title()).hasSize(100);
		assertThat(p.memo()).hasSize(2000);
	}

	@Test
	void privateHostsAreRefused() {
		assertThat(ImportJobService.isPrivateHost("localhost")).isTrue();
		assertThat(ImportJobService.isPrivateHost("api.localhost")).isTrue();
		assertThat(ImportJobService.isPrivateHost("127.0.0.1")).isTrue();
		assertThat(ImportJobService.isPrivateHost("10.0.0.5")).isTrue();
		assertThat(ImportJobService.isPrivateHost("192.168.0.1")).isTrue();
		assertThat(ImportJobService.isPrivateHost("169.254.169.254")).isTrue();
		assertThat(ImportJobService.isPrivateHost("[::1]")).isTrue();
		assertThat(ImportJobService.isPrivateHost("www.youthcenter.go.kr")).isFalse();
		assertThat(ImportJobService.isPrivateHost("8.8.8.8")).isFalse();
	}

}
