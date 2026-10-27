package com.youthbenefit.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.ai.LlmClient;
import com.youthbenefit.ontong.OntongApiClient;
import com.youthbenefit.ontong.OntongResponse;
import com.youthbenefit.ontong.OntongSyncService;
import com.youthbenefit.policy.PolicyRepository;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
class AiSupportIntegrationTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@MockitoBean
	OntongApiClient apiClient;

	@MockitoBean
	LlmClient llm;

	@Autowired
	OntongSyncService syncService;

	@Autowired
	PolicyRepository policyRepository;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MockMvc mockMvc;

	private List<Map<String, Object>> items;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.execute("delete from notifications; delete from policy_changes; delete from checklist_items; "
				+ "delete from applications; delete from policies; delete from sync_cursors");
		try (InputStream in = getClass().getResourceAsStream("/fixtures/ontong-response.json")) {
			items = JsonMapper.builder().build().readValue(in, OntongResponse.class).result().youthPolicyList();
		}
		given(llm.configured()).willReturn(true);
		given(llm.defaultModel()).willReturn("test-model");
		// 첫 공고만 금액, 나머지는 금액 없음
		given(llm.json(anyString(), anyString(), anyString(), anyString(), any())).willReturn(JSON.readTree("""
				{"amountWon": null, "period": "", "upTo": false, "kind": "SERVICE", "evidence": "", "summary": "교육 지원"}
				"""));
		given(llm.json(anyString(), anyString(), contains("월 최대 20만원"), anyString(), any())).willReturn(JSON.readTree("""
				{"amountWon": 200000, "period": "MONTH", "upTo": true, "kind": "CASH", "evidence": "1인당 월 최대 20만원 지원", "summary": "아침밥 지원"}
				"""));
	}

	@Test
	void cardsShowAmountOrSummary() throws Exception {
		sync("○ 1인당 월 최대 20만원 지원\n○ 기타 안내");
		Long id = policyId();
		// 뽑기 전: 지원 내용 첫 줄
		mockMvc.perform(get("/api/v1/policies/{id}", id))
			.andExpect(jsonPath("$.support.label").doesNotExist())
			.andExpect(jsonPath("$.support.summary").value("1인당 월 최대 20만원 지원"))
			.andExpect(jsonPath("$.support.source").value("TEXT"));

		mockMvc.perform(post("/api/v1/admin/ai/support").param("limit", "100"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.withAmount").value(1))
			.andExpect(jsonPath("$.failed").value(0));

		mockMvc.perform(get("/api/v1/policies/{id}", id))
			.andExpect(jsonPath("$.support.label").value("월 최대 20만원"))
			.andExpect(jsonPath("$.support.amountWon").value(200000))
			.andExpect(jsonPath("$.support.summary").value("아침밥 지원"))
			.andExpect(jsonPath("$.support.source").value("AI"));
		// 목록 카드에도
		mockMvc.perform(get("/api/v1/policies").param("size", "100"))
			.andExpect(jsonPath("$.content[?(@.id == " + id + ")].support.label").value("월 최대 20만원"));

		// 지원 내용이 바뀌면 옛 금액은 안 쓴다
		sync("○ 1인당 월 최대 30만원 지원");
		mockMvc.perform(get("/api/v1/policies/{id}", id))
			.andExpect(jsonPath("$.support.label").doesNotExist())
			.andExpect(jsonPath("$.support.source").value("TEXT"));
	}

	private void sync(String supportContent) {
		Map<String, Object> first = new LinkedHashMap<>(items.get(0));
		first.put("plcySprtCn", supportContent);
		List<Map<String, Object>> changed = new ArrayList<>(items);
		changed.set(0, first);
		given(apiClient.fetchAll()).willReturn(changed);
		syncService.sync();
	}

	private Long policyId() {
		return policyRepository.findAll()
			.stream()
			.filter(p -> p.getTitle().startsWith("대학생 천원의 아침밥"))
			.findFirst()
			.orElseThrow()
			.getId();
	}

}
