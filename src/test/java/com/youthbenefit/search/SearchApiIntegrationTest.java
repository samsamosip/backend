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
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
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
		mockMvc.perform(get("/api/v1/search").param("q", "대학생 아침밥"))
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
		mockMvc.perform(get("/api/v1/search").param("q", "강아지 미용"))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.message").value("조건에 맞는 공고를 못 찾았어요. 다른 말로 검색해 보세요"));
	}

	@Test
	void aiFailureFallsBackToWords() throws Exception {
		given(llm.json(anyString(), anyString(), anyString(), anyString(), any()))
			.willThrow(new LlmException("한도 초과"));
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.understood.ai").value(false))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void blankQueryIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/search").param("q", " ")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.q").exists());
	}

	private void answer(String query, String json) {
		given(llm.json(anyString(), anyString(), eq(query), anyString(), any())).willReturn(JSON.readTree(json));
	}

}
