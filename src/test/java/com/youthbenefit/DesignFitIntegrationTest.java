package com.youthbenefit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.youthbenefit.ai.LlmClient;
import com.youthbenefit.ontong.OntongApiClient;
import com.youthbenefit.ontong.OntongResponse;
import com.youthbenefit.ontong.OntongSyncService;
import com.youthbenefit.policy.PolicyRepository;
import java.io.InputStream;
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

/** 최종 디자인과 맞춘 것들: 원문 링크·서류 목록, 추천순, 검색 내 지역 칩·칩 빼기, 전공 직접 입력 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class DesignFitIntegrationTest {

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

	private final String me = UUID.randomUUID().toString();

	@BeforeEach
	void setUp() throws Exception {
		jdbc.execute("delete from notifications; delete from policy_changes; delete from checklist_items; "
				+ "delete from applications; delete from user_profiles; delete from policies; delete from sync_cursors");
		List<Map<String, Object>> items;
		try (InputStream in = getClass().getResourceAsStream("/fixtures/ontong-response.json")) {
			items = JsonMapper.builder().build().readValue(in, OntongResponse.class).result().youthPolicyList();
		}
		given(apiClient.fetchAll()).willReturn(items);
		syncService.sync();
		// 대전 동구 사는 25살
		mockMvc.perform(put("/api/v1/me/profile").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"birthDate\":\"2001-01-01\",\"zipCd\":\"30110\"}")).andExpect(status().isOk());
	}

	@Test
	void detailHasSourceLinkAndDocumentList() throws Exception {
		Long id = policyId("경기청년 평화통일");
		mockMvc.perform(get("/api/v1/policies/{id}", id))
			.andExpect(jsonPath("$.sourceUrl").value(
					"https://www.youthcenter.go.kr/youthPolicy/ythPlcyTotalSearch/ythPlcyDetail/20261002005400213802"))
			.andExpect(jsonPath("$.documents.length()").value(org.hamcrest.Matchers.greaterThan(0)))
			.andExpect(jsonPath("$.documents[0].name").isNotEmpty());
	}

	@Test
	void recommendedSortPutsMatchesFirst() throws Exception {
		String body = mockMvc.perform(get("/api/v1/me/matches").param("includeNoMatch", "true").header("X-Anonymous-Id", me))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> verdicts = JsonPath.read(body, "$.content[*].verdict");
		List<String> order = List.of("MATCH", "NEEDS_CHECK", "NO_MATCH");
		assertThat(verdicts).isSortedAccordingTo((a, b) -> order.indexOf(a) - order.indexOf(b));
		mockMvc.perform(get("/api/v1/me/matches").param("sort", "deadline").header("X-Anonymous-Id", me))
			.andExpect(status().isOk());
	}

	@Test
	void searchUsesMyRegionUnlessRemoved() throws Exception {
		given(llm.configured()).willReturn(true);
		given(llm.defaultModel()).willReturn("m");
		given(llm.json(anyString(), anyString(), anyString(), anyString(), any())).willReturn(JSON.readTree("""
				{"need": [["청년"], ["아침밥", "조식"]], "who": [], "categories": [], "region": "", "age": null, "closingSoon": false}
				"""));
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.understood.region").value("대전"))
			.andExpect(jsonPath("$.understood.regionFromProfile").value(true))
			.andExpect(jsonPath("$.content[0].policy.title").value(org.hamcrest.Matchers.startsWith("대학생 천원의 아침밥")));
		// "내 지역" 칩을 ×로 뺌
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥").param("noRegion", "true").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.understood.region").doesNotExist())
			.andExpect(jsonPath("$.understood.regionFromProfile").value(false));
		// 단어 묶음 칩을 ×로 뺌
		mockMvc.perform(get("/api/v1/search").param("q", "아침밥").param("drop", "아침밥").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.understood.keywords.length()").value(0));
	}

	@Test
	void majorNameSetsCategory() throws Exception {
		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"majorName\":\"전기전자공학\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.majorName").value("전기전자공학"))
			.andExpect(jsonPath("$.plcyMajorCd").value("0011005"));
		// 전공 이름만 바꾸면 계열도 다시
		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"majorName\":\"경영학과\"}"))
			.andExpect(jsonPath("$.plcyMajorCd").value("0011003"));
		// 모르는 전공이면 계열은 모름
		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"majorName\":\"자유전공\"}"))
			.andExpect(jsonPath("$.plcyMajorCd").doesNotExist());
	}

	private Long policyId(String titlePrefix) {
		return policyRepository.findAll()
			.stream()
			.filter(p -> p.getTitle().startsWith(titlePrefix))
			.findFirst()
			.orElseThrow()
			.getId();
	}

}
