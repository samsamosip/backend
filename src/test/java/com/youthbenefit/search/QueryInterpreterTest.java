package com.youthbenefit.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class QueryInterpreterTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void parseKeepsValidPartsOnly() {
		SearchQuery q = QueryInterpreter.parse(JSON.readTree("""
				{"need": [["지원", "혜택"], ["월세", "지원", "주거비"]], "who": [["대학생", "재학생"]],
				 "categories": ["주거", "취미", "주거"], "region": "서울", "age": 24, "closingSoon": false}
				"""));
		// "지원" 묶음은 통째로, 묶음 안의 "지원"도 빠진다
		assertThat(q.keywords()).containsExactly(List.of("월세", "주거비"));
		assertThat(q.audience()).containsExactly(List.of("대학생", "재학생"));
		assertThat(q.categories()).containsExactly("주거");
		assertThat(q.regionCodes()).containsExactly("11");
		assertThat(q.age()).isEqualTo(24);
		assertThat(q.ai()).isTrue();
	}

	@Test
	void unknownRegionAndBadAgeAreDropped() {
		SearchQuery q = QueryInterpreter.parse(JSON.readTree("""
				{"need": [["창업"]], "who": [], "categories": [], "region": "우리 동네", "age": 300, "closingSoon": true}
				"""));
		assertThat(q.region()).isNull();
		assertThat(q.regionCodes()).isEmpty();
		assertThat(q.age()).isNull();
		assertThat(q.closingSoon()).isTrue();
	}

	@Test
	void wordsFallbackWithoutAi() {
		SearchQuery q = QueryInterpreter.words("부산에 사는 청년 전세 대출 지원 알려줘");
		assertThat(q.ai()).isFalse();
		assertThat(q.region()).isEqualTo("부산");
		assertThat(q.regionCodes()).containsExactly("26");
		assertThat(q.keywords()).containsExactly(List.of("전세"), List.of("대출"));
		assertThat(QueryInterpreter.words("주거 월세").categories()).containsExactly("주거");
	}

}
