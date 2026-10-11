package com.youthbenefit.harvester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.application.ApplicationRepository;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class HarvesterSyncIntegrationTest {

	private static final String OPEN_ID = "11111111-1111-4111-8111-111111111111";

	private static final String OLD_ID = "22222222-2222-4222-8222-222222222222";

	@MockitoBean
	HarvesterApiClient apiClient;

	@Autowired
	HarvesterSyncService syncService;

	@Autowired
	PolicyRepository policyRepository;

	@Autowired
	ApplicationRepository applicationRepository;

	@Autowired
	MockMvc mockMvc;

	@BeforeEach
	void setUp() throws Exception {
		applicationRepository.deleteAll();
		policyRepository.deleteAll();
		String detail;
		try (InputStream in = getClass().getResourceAsStream("/fixtures/harvester-detail.json")) {
			detail = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		// 테스트가 날짜에 따라 깨지지 않도록 신청 마감을 항상 미래로
		detail = detail.replace("2026-12-31", LocalDate.now().plusMonths(2).toString());
		given(apiClient.listAll()).willReturn(List.of(
				Map.of("opportunity_id", OPEN_ID, "version_id", "v2", "academic_year", LocalDate.now().getYear()),
				Map.of("opportunity_id", OLD_ID, "version_id", "v1", "academic_year", 2021)));
		given(apiClient.detail(OPEN_ID)).willReturn(detail);
	}

	@Test
	void syncSkipsOldYearsAndRefetchesOnlyOpenOnes() {
		HarvesterSyncResult first = syncService.sync();
		HarvesterSyncResult second = syncService.sync();

		assertThat(first).isEqualTo(new HarvesterSyncResult(2, 1, 1, 1, 0, 0, 0));
		// 아직 신청 가능한 공고는 상태가 바뀔 수 있어 다시 받지만 내용이 같으면 그대로
		assertThat(second).isEqualTo(new HarvesterSyncResult(2, 1, 1, 0, 0, 1, 0));
		verify(apiClient, never()).detail(OLD_ID);
		verify(apiClient, times(2)).detail(OPEN_ID);
		assertThat(policyRepository.count()).isEqualTo(1);
	}

	@Test
	void scholarshipShowsUpInListMatchesAndChecklist() throws Exception {
		syncService.sync();
		Long id = policyRepository.findAll().stream().map(Policy::getId).findFirst().orElseThrow();
		String me = UUID.randomUUID().toString();

		mockMvc.perform(get("/api/v1/policies").param("category", "교육"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].title").value("2026학년도 2학기 인천 우수학생 장학금"))
			.andExpect(jsonPath("$.content[0].regions[0]").value("인천"));

		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"zipCd":"28185","schoolCd":"0049005","schoolName":"인하대","enrollmentStatus":"ENROLLED",
					 "gradeLevel":3,"gpa":3.8,"gpaScale":4.5,"kosafIncomeBracket":5}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enrollmentStatus").value("ENROLLED"));

		mockMvc.perform(get("/api/v1/policies/{id}/eligibility", id).header("X-Anonymous-Id", me))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.verdict").value("NEEDS_CHECK"))
			.andExpect(jsonPath("$.conditions[?(@.label == '성적')].truth").value("TRUE"));

		mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"policyId\":" + id + "}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].content").value("장학금 신청서 (첨부파일 참조)"))
			.andExpect(jsonPath("$.items[1].optional").value(true));
	}

	@Test
	void badSchoolProfileValuesAreRejected() throws Exception {
		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"gpa\":3.8}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.gpaScale").exists());

		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"gpa\":4.8,\"gpaScale\":4.5,\"enrollmentStatus\":\"GRADUATED\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.enrollmentStatus").exists());

		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"gpa\":4.8,\"gpaScale\":4.5}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.gpa").value("평점이 만점보다 클 수 없습니다"));
	}

}
