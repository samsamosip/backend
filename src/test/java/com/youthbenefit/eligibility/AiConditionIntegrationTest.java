package com.youthbenefit.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class AiConditionIntegrationTest {

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

	private final String owner = UUID.randomUUID().toString();

	@BeforeEach
	void setUp() throws Exception {
		jdbc.execute("delete from notifications; delete from policy_changes; delete from checklist_items; "
				+ "delete from applications; delete from user_profiles; delete from policies; delete from sync_cursors");
		try (InputStream in = getClass().getResourceAsStream("/fixtures/ontong-response.json")) {
			items = JsonMapper.builder().build().readValue(in, OntongResponse.class).result().youthPolicyList();
		}
		given(llm.configured()).willReturn(true);
		given(llm.defaultModel()).willReturn("test-model");
		given(llm.json(anyString(), anyString(), anyString(), anyString(), any())).willReturn(JSON.readTree("""
				{"noExtraConditions": false, "confidence": "high",
				 "requirements": [{"var": "homeowner", "op": "IN", "values": ["false"], "evidence": "무주택 청년"}],
				 "unresolved": [{"label": "예술인", "text": "청년예술인", "exclusion": false}]}
				"""));
		mockMvc.perform(put("/api/v1/me/profile").header("X-Anonymous-Id", owner)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"homeowner\": true}")).andExpect(status().isOk());
	}

	@Test
	void convertedRulesJudgeAndChangedTextFallsBack() throws Exception {
		sync("무주택 청년예술인");
		Long id = policyId();
		// 변환 전: 원문 확인 필요
		mockMvc.perform(get("/api/v1/policies/{id}/eligibility", id).header("X-Anonymous-Id", owner))
			.andExpect(jsonPath("$.conditions[?(@.label == '추가 자격 조건')].truth").value("UNKNOWN"));

		mockMvc.perform(post("/api/v1/admin/ai/conditions").param("limit", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.converted").value(1))
			.andExpect(jsonPath("$.failed").value(0));

		// 변환 후: 집이 있는 사람은 불일치, 표현 못 한 조건은 확인 필요로 남음
		mockMvc.perform(get("/api/v1/policies/{id}/eligibility", id).header("X-Anonymous-Id", owner))
			.andExpect(jsonPath("$.verdict").value("NO_MATCH"))
			.andExpect(jsonPath("$.conditions[?(@.label == '주택 소유')].truth").value("FALSE"))
			.andExpect(jsonPath("$.conditions[?(@.label == '주택 소유')].evidence").value("AI가 원문에서 읽음: 무주택 청년"))
			.andExpect(jsonPath("$.conditions[?(@.label == '예술인')].truth").value("UNKNOWN"));

		// 같은 글이면 다시 변환하지 않는다
		mockMvc.perform(post("/api/v1/admin/ai/conditions")).andExpect(jsonPath("$.pending").value(0));
		verify(llm, times(1)).json(anyString(), anyString(), anyString(), anyString(), any());

		// 저장된 답으로 다시 검사 (AI 호출 없음)
		mockMvc.perform(post("/api/v1/admin/ai/conditions/recheck")).andExpect(status().isOk());
		verify(llm, times(1)).json(anyString(), anyString(), anyString(), anyString(), any());

		// 글이 바뀌면 옛 변환은 안 쓰고 원래대로(확인 필요), 다시 변환 대상
		sync("무주택 청년예술인 (서울 거주)");
		mockMvc.perform(get("/api/v1/policies/{id}/eligibility", id).header("X-Anonymous-Id", owner))
			.andExpect(jsonPath("$.conditions[?(@.label == '주택 소유')]").isEmpty())
			.andExpect(jsonPath("$.conditions[?(@.label == '추가 자격 조건')].truth").value("UNKNOWN"));
		mockMvc.perform(post("/api/v1/admin/ai/conditions").param("limit", "0"))
			.andExpect(jsonPath("$.pending").value(1));
	}

	@Test
	void aiConversionAloneIsNotAPolicyChange() throws Exception {
		sync("무주택 청년예술인");
		Long id = policyId();
		mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", owner)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"policyId\":" + id + "}")).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/admin/ai/conditions"));

		// 조회수만 바뀐 재수집: 원본 해시는 바뀌지만 AI 결과가 붙었다고 "자격 조건 변경" 알림이 나가면 안 된다
		Map<String, Object> first = new LinkedHashMap<>(items.get(0));
		first.put("addAplyQlfcCndCn", "무주택 청년예술인");
		first.put("inqCnt", "123456");
		List<Map<String, Object>> changed = new ArrayList<>(items);
		changed.set(0, first);
		given(apiClient.fetchAll()).willReturn(changed);
		syncService.sync();
		assertThat(jdbc.queryForObject("select count(*) from notifications", Integer.class)).isZero();
	}

	@Test
	void notConfiguredMeansNoCalls() throws Exception {
		given(llm.configured()).willReturn(false);
		sync("무주택 청년예술인");
		mockMvc.perform(post("/api/v1/admin/ai/conditions")).andExpect(status().isServiceUnavailable());
		verify(llm, never()).json(anyString(), anyString(), anyString(), anyString(), any());
	}

	private void sync(String additional) {
		Map<String, Object> first = new LinkedHashMap<>(items.get(0));
		first.put("addAplyQlfcCndCn", additional);
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
