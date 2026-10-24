package com.youthbenefit.eligibility;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.ontong.OntongApiClient;
import com.youthbenefit.ontong.OntongResponse;
import com.youthbenefit.ontong.OntongSyncService;
import com.youthbenefit.policy.PolicyRepository;
import java.io.InputStream;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class EligibilityApiIntegrationTest {

	@MockitoBean
	OntongApiClient apiClient;

	@Autowired
	org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	@Autowired
	OntongSyncService syncService;

	@Autowired
	PolicyRepository policyRepository;

	@Autowired
	MockMvc mockMvc;

	@BeforeEach
	void setUp() throws Exception {
		// 변경 이력·알림·준비함이 공고를 참조해서 먼저 지운다 (다른 테스트가 남긴 것 포함)
		jdbcTemplate.execute("delete from notifications; delete from policy_changes; delete from checklist_items; delete from applications; delete from sync_cursors");
		policyRepository.deleteAll();
		try (InputStream in = getClass().getResourceAsStream("/fixtures/ontong-response.json")) {
			given(apiClient.fetchAll()).willReturn(
					JsonMapper.builder().build().readValue(in, OntongResponse.class).result().youthPolicyList());
		}
		syncService.sync();
	}

	@Test
	void matchesUseMyProfile() throws Exception {
		String id = UUID.randomUUID().toString();
		// 대전 거주 대학생 → "대학생 천원의 아침밥"(대전·대학 재학) 은 추가 자격 조건 때문에 확인 필요
		mockMvc.perform(put("/api/v1/me/profile").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"birthDate\":\"2003-06-12\",\"zipCd\":\"30110\",\"schoolCd\":\"0049005\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/me/matches").header("X-Anonymous-Id", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.match").isNumber())
			.andExpect(jsonPath("$.content[*].verdict").value(Matchers.not(Matchers.hasItem("NO_MATCH"))))
			.andExpect(jsonPath("$.content[?(@.policy.title == '대학생 천원의 아침밥 지원 사업')].verdict")
				.value(Matchers.contains("NEEDS_CHECK")));

		Long breakfastId = policyRepository.findAll()
			.stream()
			.filter(p -> p.getTitle().equals("대학생 천원의 아침밥 지원 사업"))
			.findFirst()
			.orElseThrow()
			.getId();
		mockMvc.perform(get("/api/v1/policies/{id}/eligibility", breakfastId).header("X-Anonymous-Id", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.verdictLabel").value("확인 필요"))
			.andExpect(jsonPath("$.conditions[?(@.label == '지역')].truth").value(Matchers.contains("TRUE")))
			.andExpect(jsonPath("$.conditions[?(@.label == '학력')].truth").value(Matchers.contains("TRUE")))
			.andExpect(jsonPath("$.summary").value("추가 자격 조건 확인 필요"));
	}

	@Test
	void withoutProfileEverythingWithConditionsNeedsCheck() throws Exception {
		mockMvc.perform(get("/api/v1/me/matches").param("includeNoMatch", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.noMatch").value(0));
	}

	@Test
	void closingThisWeekOnlyHasDatedPoliciesInTheWindow() throws Exception {
		// 고용환경개선사업은 2026-12-31 마감, 대학생 천원의 아침밥은 2027-02-28 마감, 직장맘지원센터는 상시 모집
		long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")),
				java.time.LocalDate.of(2026, 12, 31));
		org.junit.jupiter.api.Assumptions.assumeTrue(days >= 0, "고정 데이터의 마감일이 지났다");

		mockMvc.perform(get("/api/v1/me/matches").param("closingWithinDays", String.valueOf(days)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].policy.title").value(Matchers.contains("고용환경개선사업")))
			.andExpect(jsonPath("$.content[0].dDay").value((int) days));
		mockMvc.perform(get("/api/v1/me/matches"))
			.andExpect(jsonPath("$.content[?(@.policy.title == '직장맘지원센터 운영')].dDay")
				.value(Matchers.contains((Object) null)));
	}

	@Test
	void suggestionsRankFieldsThatDecideMostPolicies() throws Exception {
		String id = UUID.randomUUID().toString();
		// 세종 거주, 나이만 앎 → 고용환경개선사업(나이·세종·재직자)은 취업 상태만 알면 확정,
		// 직장맘지원센터(세종·취업 상태·기혼)는 취업 상태와 결혼 여부 둘 다 필요
		mockMvc.perform(put("/api/v1/me/profile").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"birthDate\":\"2000-05-01\",\"zipCd\":\"36110\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/me/suggestions").header("X-Anonymous-Id", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].field").value("jobCd"))
			.andExpect(jsonPath("$.items[0].resolvable").value(1))
			.andExpect(jsonPath("$.items[0].affected").value(2))
			.andExpect(jsonPath("$.items[0].message").value("취업 상태를 입력하면 공고 1개가 더 판정돼요"))
			.andExpect(jsonPath("$.items[1].field").value("mrgSttsCd"))
			.andExpect(jsonPath("$.items[1].message").value("결혼 여부를 입력하면 공고 1개를 더 정확히 볼 수 있어요"));
	}

}
