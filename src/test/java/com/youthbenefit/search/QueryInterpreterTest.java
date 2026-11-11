package com.youthbenefit.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.youthbenefit.ai.LlmClient;
import com.youthbenefit.ai.LlmException;

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

	@Test
	void personalizedRequestKeepsAnEmptyAiQueryAndCachesIt() {
		LlmClient llm = mock(LlmClient.class);
		when(llm.configured()).thenReturn(true);
		when(llm.defaultModel()).thenReturn("test");
		when(llm.json(anyString(), anyString(), eq("나한테 맞는거 해줘."), anyString(), any())).thenReturn(JSON.readTree("""
				{"need":[],"who":[],"categories":[],"region":"","age":null,"closingSoon":false}
				"""));
		QueryInterpreter interpreter = new QueryInterpreter(llm);
		SearchQuery query = interpreter.interpret("나한테 맞는거 해줘.");
		assertThat(query.ai()).isTrue();
		assertThat(query.hasFilters()).isFalse();
		assertThat(interpreter.interpret("나한테 맞는거 해줘.", false)).isEqualTo(query);
		verify(llm, times(1)).json(anyString(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void providerFailureStillUsesWordsButRecommendationWordsDoNotBecomeKeywords() {
		LlmClient llm = mock(LlmClient.class);
		when(llm.configured()).thenReturn(true);
		when(llm.defaultModel()).thenReturn("test");
		when(llm.json(anyString(), anyString(), anyString(), anyString(), any())).thenThrow(new LlmException("unavailable"));
		QueryInterpreter interpreter = new QueryInterpreter(llm);
		SearchQuery query = interpreter.interpret("나한테 맞는거 해줘.");
		assertThat(query.ai()).isFalse();
		assertThat(query.keywords()).isEmpty();
		assertThat(interpreter.interpret("서울 월세").keywords()).containsExactly(List.of("월세"));
	}

	@Test
	void malformedEmptyAnswerDoesNotBecomeAnUnfilteredSearch() {
		assertThatThrownBy(() -> QueryInterpreter.parse(JSON.readTree("{}"))).isInstanceOf(IllegalArgumentException.class);
		LlmClient llm = mock(LlmClient.class);
		when(llm.configured()).thenReturn(true);
		when(llm.defaultModel()).thenReturn("test");
		when(llm.json(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(JSON.readTree("{}"));
		SearchQuery query = new QueryInterpreter(llm).interpret("부산 월세");
		assertThat(query.ai()).isFalse();
		assertThat(query.region()).isEqualTo("부산");
		assertThat(query.keywords()).containsExactly(List.of("월세"));
	}

}
