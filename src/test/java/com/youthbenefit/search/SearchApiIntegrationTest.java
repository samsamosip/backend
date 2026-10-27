package com.youthbenefit.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.ai.LlmClient;
import com.youthbenefit.ai.LlmException;
import com.youthbenefit.ontong.OntongApiClient;
import com.youthbenefit.ontong.OntongResponse;
import com.youthbenefit.ontong.OntongSyncService;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "spring.docker.compose.enabled=false", "search.rate-limit.per-minute=6",
		"search.rate-limit.ai-per-hour=2" })
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class SearchApiIntegrationTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@MockitoBean
	OntongApiClient apiClient;

	@MockitoBean
	LlmClient llm;

	@Autowired
	OntongSyncService syncService;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MockMvc mockMvc;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.execute("delete from notifications; delete from policy_changes; delete from checklist_items; "
				+ "delete from applications; delete from policies; delete from sync_cursors");
		List<Map<String, Object>> items;
		try (InputStream in = getClass().getResourceAsStream("/fixtures/ontong-response.json")) {
			items = JsonMapper.builder().build().readValue(in, OntongResponse.class).result().youthPolicyList();
		}
		given(apiClient.fetchAll()).willReturn(items);
		syncService.sync();
		given(llm.configured()).willReturn(true);
		given(llm.defaultModel()).willReturn("test-model");
	}

	@Test
	void aiUnderstandsAndCodeFinds() throws Exception {
		answer("대학생 아침밥", """
				{"need": [["아침밥", "식사", "조식"]], "who": [["대학생"]], "categories": [], "region": "", "age": null, "closingSoon": false}
				""");
		mockMvc.perform(get("/api/v1/search").param("q", "대학생 아침밥").header("X-Anonymous-Id", newDevice()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.understood.ai").value(true))
			.andExpect(jsonPath("$.understood.keywords[0][0]").value("아침밥"))
			.andExpect(jsonPath("$.understood.audience[0][0]").value("대학생"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].policy.title").value(org.hamcrest.Matchers.startsWith("대학생 천원의 아침밥")))
			.andExpect(jsonPath("$.content[0].verdict").exists())
			.andExpect(jsonPath("$.message").doesNotExist());
	}

	@Test
	void noResultSaysSo() throws Exception {
		answer("강아지 미용", """
				{"need": [["애견미용"]], "who": [], "categories": [], "region": "", "age": null, "closingSoon": false}
				""");
		mockMvc.perform(get("/api/v1/search").param("q", "강아지 미용").header("X-Anonymous-Id", newDevice()))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.message").value("조건에 맞는 공고를 못 찾았어요. 다른 말로 검색해 보세요"));
	}

	@Test
	void aiFailureFallsBackToWords() throws Exception {
		given(llm.json(anyString(), anyString(), anyString(), anyString(), any()))
			.willThrow(new LlmException("한도 초과"));
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥").header("X-Anonymous-Id", newDevice()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.understood.ai").value(false))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void tooManySearchesAreLimitedAndAiFallsBackToWords() throws Exception {
		given(llm.json(anyString(), anyString(), anyString(), anyString(), any())).willReturn(JSON.readTree("""
				{"need": [["아침밥"]], "who": [], "categories": [], "region": "", "age": null, "closingSoon": false}
				"""));
		String device = java.util.UUID.randomUUID().toString();
		// 1시간에 AI 2번까지, 그다음은 단어로 (오류 아님)
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(get("/api/v1/search").param("q", "아침밥 " + i).header("X-Anonymous-Id", device))
				.andExpect(jsonPath("$.understood.ai").value(true))
				.andExpect(jsonPath("$.notice").doesNotExist());
		}
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥 2").header("X-Anonymous-Id", device))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.understood.ai").value(false))
			.andExpect(jsonPath("$.notice").value("지금은 AI 검색을 쉬고 있어서 단어로 찾았어요"));
		// 기억해 둔 문장은 한도와 상관없이 AI 해석 그대로
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥 0").header("X-Anonymous-Id", device))
			.andExpect(jsonPath("$.understood.ai").value(true));
		// 1분에 6번까지, 7번째는 429
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥").header("X-Anonymous-Id", device))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥").header("X-Anonymous-Id", device))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥").header("X-Anonymous-Id", device))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
		// 다른 기기는 따로
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥").header("X-Anonymous-Id", java.util.UUID.randomUUID().toString()))
			.andExpect(status().isOk());
	}

	@Test
	void dailyAiLimitMeansWordSearch() throws Exception {
		given(llm.overDailyLimit()).willReturn(true);
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥 한도").header("X-Anonymous-Id", newDevice()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.understood.ai").value(false));
		org.mockito.Mockito.verify(llm, org.mockito.Mockito.never())
			.json(anyString(), anyString(), eq("아침밥 한도"), anyString(), any());
	}

	@Test
	void blankQueryIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/search").param("q", " ")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.q").exists());
	}

	/** 테스트마다 다른 기기 — 사용량 제한을 따로 센다 */
	private static String newDevice() {
		return java.util.UUID.randomUUID().toString();
	}

	private void answer(String query, String json) {
		given(llm.json(anyString(), anyString(), eq(query), anyString(), any())).willReturn(JSON.readTree(json));
	}

}
