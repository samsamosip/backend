package com.youthbenefit.notification;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.application.ApplicationRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class NotificationApiIntegrationTest {

	@MockitoBean
	OntongApiClient apiClient;

	@Autowired
	OntongSyncService syncService;

	@Autowired
	PolicyRepository policyRepository;

	@Autowired
	ApplicationRepository applicationRepository;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	PolicyChangeRepository changeRepository;

	@Autowired
	MockMvc mockMvc;

	private List<Map<String, Object>> items;

	@BeforeEach
	void setUp() throws Exception {
		notificationRepository.deleteAll();
		changeRepository.deleteAll();
		applicationRepository.deleteAll();
		policyRepository.deleteAll();
		try (InputStream in = getClass().getResourceAsStream("/fixtures/ontong-response.json")) {
			items = JsonMapper.builder().build().readValue(in, OntongResponse.class).result().youthPolicyList();
		}
		given(apiClient.fetchAll()).willReturn(items);
		syncService.sync();
	}

	@Test
	void savedPolicyChangeNotifiesOnlyItsOwners() throws Exception {
		Long policyId = policyRepository.findAll()
			.stream()
			.filter(p -> p.getTitle().startsWith("대학생 천원의 아침밥"))
			.findFirst()
			.orElseThrow()
			.getId();
		String me = UUID.randomUUID().toString();
		String other = UUID.randomUUID().toString();
		mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"policyId\":" + policyId + "}")).andExpect(status().isCreated());

		// 마감 연장: 2027-02-28 → 2027-03-31, 조회수도 바뀌지만 그건 알림 대상이 아니다
		Map<String, Object> extended = new LinkedHashMap<>(items.get(0));
		extended.put("aplyYmd", "20270102 ~ 20270331");
		extended.put("inqCnt", "99999");
		List<Map<String, Object>> changed = new ArrayList<>(items);
		changed.set(0, extended);
		given(apiClient.fetchAll()).willReturn(changed);
		syncService.sync();

		String list = mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", me))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unreadCount").value(1))
			.andExpect(jsonPath("$.items[0].kind").value("EXTENDED"))
			.andExpect(jsonPath("$.items[0].type").value("CHANGE"))
			.andExpect(jsonPath("$.items[0].label").value("마감 연장"))
			.andExpect(jsonPath("$.items[0].summary").value("마감일 2027-02-28 → 2027-03-31"))
			.andExpect(jsonPath("$.items[0].read").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer id = JsonPath.read(list, "$.items[0].id");

		mockMvc.perform(get("/api/v1/me/notifications/{id}", id).header("X-Anonymous-Id", me))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.changes[0].label").value("마감일"))
			.andExpect(jsonPath("$.changes[0].before").value("2027-02-28"))
			.andExpect(jsonPath("$.changes[0].after").value("2027-03-31"))
			.andExpect(jsonPath("$.read").value(true));
		mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.unreadCount").value(0));

		// 담지 않은 사람에게는 없고, 남의 알림은 404
		mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", other))
			.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/me/notifications/{id}", id).header("X-Anonymous-Id", other))
			.andExpect(status().isNotFound());

		// 알림함 탭 (디자인 05)
		mockMvc.perform(get("/api/v1/me/notifications").param("tab", "EXTENDED").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.items.length()").value(1));
		mockMvc.perform(get("/api/v1/me/notifications").param("tab", "CORRECTED").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/api/v1/me/notifications").param("tab", "NEW").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.items.length()").value(0));

		// 공고 변경 이력은 누구나 본다
		mockMvc.perform(get("/api/v1/policies/{id}/changes", policyId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].kindLabel").value("마감 연장"));

		// 같은 내용으로 다시 수집하면 알림이 늘지 않는다
		syncService.sync();
		mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.items.length()").value(1));

		mockMvc.perform(post("/api/v1/me/notifications/read-all").header("X-Anonymous-Id", me))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/me/notifications/{id}", id).header("X-Anonymous-Id", me))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.items.length()").value(0));
	}

}
