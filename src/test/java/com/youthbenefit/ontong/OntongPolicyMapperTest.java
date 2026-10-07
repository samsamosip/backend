package com.youthbenefit.ontong;

import static org.assertj.core.api.Assertions.assertThat;

import com.youthbenefit.policy.PolicyContent;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OntongPolicyMapperTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final OntongPolicyMapper mapper = new OntongPolicyMapper(jsonMapper);

	private List<Map<String, Object>> items;

	@BeforeEach
	void loadFixture() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/fixtures/ontong-response.json")) {
			items = jsonMapper.readValue(in, OntongResponse.class).result().youthPolicyList();
		}
	}

	@Test
	void keepsApiFieldsAndCodesAsIs() {
		Map<String, Object> breakfast = items.get(0);

		PolicyContent content = mapper.toContent(breakfast);

		assertThat(mapper.sourceId(breakfast)).isEqualTo("20261002005400113836");
		assertThat(content.title()).isEqualTo("대학생 천원의 아침밥 지원 사업");
		assertThat(content.applyPeriodCode()).isEqualTo("0057001");
		assertThat(content.zipCd()).isEqualTo("30110,30140,30170,30200,30230");
		assertThat(content.schoolCd()).isEqualTo("0049005");
		assertThat(content.jobCd()).isEqualTo("0013010");
		assertThat(content.sprtTrgtMinAge()).isZero();
		assertThat(content.sourceModifiedAt()).isEqualTo(LocalDateTime.of(2026, 10, 2, 18, 31, 30));
	}

	@Test
	void addsRegionAndCategoryForScreens() {
		PolicyContent breakfast = mapper.toContent(items.get(0));

		assertThat(breakfast.sidoCodes()).isEqualTo("30");
		assertThat(breakfast.nationwide()).isFalse();
		assertThat(breakfast.categoryGroup()).isEqualTo("복지문화");
	}

	@Test
	void parsesApplyPeriod() {
		PolicyContent content = mapper.toContent(items.get(0));

		assertThat(content.applyStartDate()).isEqualTo(LocalDate.of(2027, 1, 2));
		assertThat(content.applyEndDate()).isEqualTo(LocalDate.of(2027, 2, 28));
	}

	@Test
	void multipleApplyRangesUseFirstStartAndLastEnd() {
		PolicyContent content = mapper.toContent(items.get(3));

		assertThat(content.applyPeriodRaw()).contains("\\N");
		assertThat(content.applyStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(content.applyEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
	}

	@Test
	void blankAndWhitespaceValuesBecomeNull() {
		PolicyContent alwaysOpen = mapper.toContent(items.get(1));

		assertThat(alwaysOpen.applyPeriodCode()).isEqualTo("0057002");
		assertThat(alwaysOpen.applyPeriodRaw()).isNull();
		assertThat(alwaysOpen.applyStartDate()).isNull();
		assertThat(alwaysOpen.applyEndDate()).isNull();
		assertThat(OntongPolicyMapper.text(Map.of("bizPrdBgngYmd", "        "), "bizPrdBgngYmd")).isNull();
		assertThat(OntongPolicyMapper.integer(Map.of("age", "abc"), "age")).isNull();
	}

	@Test
	void sameRawGivesSameHash() {
		PolicyContent first = mapper.toContent(items.get(0));
		PolicyContent again = mapper.toContent(items.get(0));
		PolicyContent other = mapper.toContent(items.get(1));

		assertThat(first.rawHash()).isEqualTo(again.rawHash()).hasSize(64);
		assertThat(first.rawHash()).isNotEqualTo(other.rawHash());
		assertThat(first.rawPayload()).contains("\"plcyNo\":\"20261002005400113836\"");
	}

	@Test
	void viewCountChangeIsNotAModification() {
		Map<String, Object> today = new java.util.LinkedHashMap<>(items.get(0));
		today.put("inqCnt", "999");

		PolicyContent yesterday = mapper.toContent(items.get(0));
		PolicyContent viewedMore = mapper.toContent(today);

		assertThat(viewedMore.rawHash()).isEqualTo(yesterday.rawHash());
		assertThat(viewedMore.rawPayload()).contains("\"inqCnt\":\"999\"");
	}

}
