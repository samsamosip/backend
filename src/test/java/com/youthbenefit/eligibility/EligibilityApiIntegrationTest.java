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
	OntongSyncService syncService;

	@Autowired
	PolicyRepository policyRepository;

	@Autowired
	MockMvc mockMvc;

	@BeforeEach
	void setUp() throws Exception {
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

}
