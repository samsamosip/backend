package com.youthbenefit.application;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.ontong.OntongApiClient;
import com.youthbenefit.ontong.OntongResponse;
import com.youthbenefit.ontong.OntongSyncService;
import com.youthbenefit.policy.PolicyRepository;
import java.io.InputStream;
import java.util.UUID;
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
class ApplicationApiIntegrationTest {

	@MockitoBean
	OntongApiClient apiClient;

	@Autowired
	OntongSyncService syncService;

	@Autowired
	PolicyRepository policyRepository;

	@Autowired
	ApplicationRepository applicationRepository;

	@Autowired
	MockMvc mockMvc;

	private Long policyId;

	@BeforeEach
	void setUp() throws Exception {
		applicationRepository.deleteAll();
		policyRepository.deleteAll();
		try (InputStream in = getClass().getResourceAsStream("/fixtures/ontong-response.json")) {
			given(apiClient.fetchAll()).willReturn(
					JsonMapper.builder().build().readValue(in, OntongResponse.class).result().youthPolicyList());
		}
		syncService.sync();
		// 제출 서류: "신청서, 경기도 거주사실을 증명할 수 있는 주민등록등본 등" / 마감(0057003)
		policyId = policyRepository.findAll()
			.stream()
			.filter(p -> p.getTitle().startsWith("경기청년 평화통일"))
			.findFirst()
			.orElseThrow()
			.getId();
	}

	@Test
	void saveCheckAndApply() throws Exception {
		String me = UUID.randomUUID().toString();
		String created = mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"policyId\":" + policyId + "}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("INTERESTED"))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].content").value("신청서"))
			.andExpect(jsonPath("$.progress.done").value(0))
			.andExpect(jsonPath("$.deadline.closed").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer id = JsonPath.read(created, "$.id");
		Integer firstItem = JsonPath.read(created, "$.items[0].id");

		// 두 번 눌러도 하나
		mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"policyId\":" + policyId + "}")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));

		mockMvc.perform(patch("/api/v1/me/applications/{id}/items/{item}", id, firstItem).header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"checked\":true}")).andExpect(jsonPath("$.progress.done").value(1));

		mockMvc.perform(post("/api/v1/me/applications/{id}/items", id).header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"통장 사본\"}"))
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[2].source").value("USER"));

		mockMvc.perform(patch("/api/v1/me/applications/{id}", id).header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"APPLIED\"}")).andExpect(jsonPath("$.statusLabel").value("신청 완료"));

		mockMvc.perform(get("/api/v1/me/applications").header("X-Anonymous-Id", me))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].progress.done").value(1))
			.andExpect(jsonPath("$[0].progress.total").value(3));

		mockMvc.perform(delete("/api/v1/me/applications/{id}", id).header("X-Anonymous-Id", me))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me/applications").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void othersCannotSeeMyApplication() throws Exception {
		String me = UUID.randomUUID().toString();
		String created = mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"policyId\":" + policyId + "}")).andReturn().getResponse().getContentAsString();
		Integer id = JsonPath.read(created, "$.id");

		mockMvc.perform(get("/api/v1/me/applications/{id}", id).header("X-Anonymous-Id", UUID.randomUUID().toString()))
			.andExpect(status().isNotFound());
	}

	@Test
	void deletingMyDataRemovesApplications() throws Exception {
		String me = UUID.randomUUID().toString();
		mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"policyId\":" + policyId + "}")).andExpect(status().isCreated());

		mockMvc.perform(delete("/api/v1/me").header("X-Anonymous-Id", me)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me/applications").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void wrongValuesSayWhichFieldAndWhy() throws Exception {
		String me = UUID.randomUUID().toString();
		String created = mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"policyId\":" + policyId + "}")).andReturn().getResponse().getContentAsString();
		Integer id = JsonPath.read(created, "$.id");

		mockMvc.perform(patch("/api/v1/me/applications/{id}", id).header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"DONE\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.status").value("INTERESTED, PREPARING, APPLIED 중 하나여야 합니다"));

		mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"policyId\":\"abc\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.policyId").value("숫자여야 합니다"));

		mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다. JSON 형식을 확인해 주세요."));
	}

	@Test
	void unknownPolicyIs404() throws Exception {
		mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"policyId\":999999}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("공고를 찾을 수 없습니다: 999999"));
	}

}
