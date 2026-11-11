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

	@MockitoBean
	HarvesterSemanticSearch semanticSearch;

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
		given(semanticSearch.search(any())).willReturn(HarvesterSemanticSearch.Result.NONE);
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
	void personalizedSearchUsesEachOwnersProfileWithoutInventedKeywords() throws Exception {
		String q = "나한테 맞는거 해줘.";
		answer(q, """
				{"need":[],"who":[],"categories":[],"region":"","age":null,"closingSoon":false}
				""");
		// Restrict the controlled open-policy fixture so the profile must affect eligibility.
		jdbc.update("update policies set nationwide=true, zip_cd=null, school_cd='0049005', "
				+ "mrg_stts_cd='0055003', job_cd='0013010', sprt_trgt_age_lmt_yn='Y', "
				+ "sprt_trgt_min_age=0, sprt_trgt_max_age=0, add_aply_qlfc_cnd_cn=null, ptcp_prp_trgt_cn=null, updated_at=now()");
		String student = newDevice();
		String graduate = newDevice();
		jdbc.update("insert into user_profiles (anonymous_id, school_cd, created_at, updated_at) values (?::uuid, '0049005', now(), now())", student);
		jdbc.update("insert into user_profiles (anonymous_id, school_cd, created_at, updated_at) values (?::uuid, '0049007', now(), now())", graduate);
		mockMvc.perform(get("/api/v1/search").param("q", q).header("X-Anonymous-Id", student))
			.andExpect(status().isOk()).andExpect(jsonPath("$.understood.ai").value(true))
			.andExpect(jsonPath("$.understood.keywords").isEmpty())
			.andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)))
			.andExpect(jsonPath("$.notice").doesNotExist());
		// The same cached interpretation must not reuse the previous owner's profile.
		mockMvc.perform(get("/api/v1/search").param("q", q).header("X-Anonymous-Id", graduate))
			.andExpect(status().isOk()).andExpect(jsonPath("$.understood.ai").value(true))
			.andExpect(jsonPath("$.totalElements").value(0));
		// No profile means unknown eligibility; no age/school facts should be fabricated.
		mockMvc.perform(get("/api/v1/search").param("q", q).header("X-Anonymous-Id", newDevice()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.understood.age").doesNotExist())
			.andExpect(jsonPath("$.understood.audience").isEmpty())
			.andExpect(jsonPath("$.counts.needsCheck").value(org.hamcrest.Matchers.greaterThan(0)));
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
	void semanticSchoolHitIsMergedOnceAndStillJudgedAgainstTheOwnersProfile() throws Exception {
		String sourceId = "6e08fb20-aaf4-4db2-92bb-f91c085b448c";
		jdbc.update("update policies set source='SCHOOL', source_id=?, title='별빛 희망 장학생', "
				+ "description='학업을 이어갈 청년에게 지원', support_content='장학 사업', keywords=null, "
				+ "category_group='교육', nationwide=true, zip_cd=null, apply_start_date=null, apply_end_date=null, "
				+ "apply_period_code='0057002', raw_payload=cast(? as jsonb), updated_at=now() "
				+ "where source_id='20261002005400113836'", sourceId,
				"{\"eligibility\":{\"academic_level_rule_status\":\"restricted\",\"academic_levels\":[\"undergraduate\"]}}");
		answer("등록금 부담", """
				{"need":[["등록금"]],"who":[],"categories":["교육"],"region":"","age":null,"closingSoon":false}
				""");
		given(semanticSearch.search(any())).willReturn(new HarvesterSemanticSearch.Result(true, List.of(
				new HarvesterSemanticSearch.Hit(sourceId, .65), new HarvesterSemanticSearch.Hit("not-synced", .9),
				new HarvesterSemanticSearch.Hit(sourceId, .7))));
		mockMvc.perform(get("/api/v1/search").param("q", "등록금 부담").header("X-Anonymous-Id", newDevice()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].policy.title").value("별빛 희망 장학생"))
			.andExpect(jsonPath("$.content[0].policy.source").value("SCHOOL"))
			.andExpect(jsonPath("$.semanticSearch.used").value(true))
			.andExpect(jsonPath("$.semanticSearch.added").value(1));
		String graduate = newDevice();
		jdbc.update("insert into user_profiles (anonymous_id, school_cd, created_at, updated_at) values (?::uuid, '0049007', now(), now())", graduate);
		mockMvc.perform(get("/api/v1/search").param("q", "등록금 부담").header("X-Anonymous-Id", graduate))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.counts.noMatch").value(1));
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
