package com.youthbenefit.ontong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.policy.PolicyRepository;
import java.io.IOException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class OntongSyncIntegrationTest {

	@MockitoBean
	OntongApiClient apiClient;

	@Autowired
	OntongSyncService syncService;

	@Autowired
	PolicyRepository policyRepository;

	@Autowired
	MockMvc mockMvc;

	private List<Map<String, Object>> items;

	@BeforeEach
	void setUp() throws IOException {
		policyRepository.deleteAll();
		try (InputStream in = getClass().getResourceAsStream("/fixtures/ontong-response.json")) {
			items = JsonMapper.builder().build().readValue(in, OntongResponse.class).result().youthPolicyList();
		}
		given(apiClient.fetchAll()).willReturn(items);
	}

	@Test
	void secondSyncOnlyCountsUnchanged() {
		OntongSyncResult first = syncService.sync();
		OntongSyncResult second = syncService.sync();

		assertThat(first).isEqualTo(new OntongSyncResult(4, 4, 0, 0));
		assertThat(second).isEqualTo(new OntongSyncResult(4, 0, 0, 4));
		assertThat(policyRepository.count()).isEqualTo(4);
	}

	@Test
	void listHidesClosedPolicies() throws Exception {
		syncService.sync();

		// 마감(0057003) 공고와 신청 기간이 지난 공고는 목록에서 빠진다
		mockMvc.perform(get("/api/v1/policies"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].applyPeriodCode")
				.value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("0057003"))))
			.andExpect(jsonPath("$.content[0].zipCd").doesNotExist());
	}

	@Test
	void filtersByOfficialCategory() throws Exception {
		syncService.sync();

		// 원본 대분류는 "금융･복지･문화"지만 공식 이름 "복지문화"로 찾는다
		mockMvc.perform(get("/api/v1/policies").param("category", "복지문화"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].title").value(org.hamcrest.Matchers.hasItem("대학생 천원의 아침밥 지원 사업")))
			.andExpect(jsonPath("$.content[0].categories[0]").value("복지문화"));
	}

}
