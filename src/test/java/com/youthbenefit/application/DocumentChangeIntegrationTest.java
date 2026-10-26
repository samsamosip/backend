package com.youthbenefit.application;

import static org.mockito.BDDMockito.given;
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

/** 공고 제출 서류가 바뀌면 담은 사람 체크리스트에 반영된다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class DocumentChangeIntegrationTest {

	@MockitoBean
	OntongApiClient apiClient;

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
	}

	@Test
	void addedDocumentsAppearAndDroppedOnesAreMarkedNotDeleted() throws Exception {
		sync("주민등록등본\n재학증명서\n통장 사본");
		Long policyId = policyRepository.findAll()
			.stream()
			.filter(p -> p.getTitle().startsWith("대학생 천원의 아침밥"))
			.findFirst()
			.orElseThrow()
			.getId();
		String me = UUID.randomUUID().toString();
		String body = mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"policyId\":" + policyId + "}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].policyChange").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer appId = JsonPath.read(body, "$.id");
		Integer resident = JsonPath.read(body, "$.items[0].id");
		Integer student = JsonPath.read(body, "$.items[1].id");
		check(me, appId, resident);
		check(me, appId, student);
		mockMvc.perform(post("/api/v1/me/applications/{id}/items", appId).header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"내가 챙길 것\"}")).andExpect(status().isOk());

		// 재학증명서 빠짐, 가족관계증명서 추가, 띄어쓰기만 바뀐 "통장사본"은 같은 서류
		sync("주민등록 등본\n통장사본\n가족관계증명서");

		mockMvc.perform(get("/api/v1/me/applications/{id}", appId).header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.items.length()").value(5))
			.andExpect(jsonPath("$.items[0].content").value("주민등록등본"))
			.andExpect(jsonPath("$.items[0].checked").value(true))
			.andExpect(jsonPath("$.items[0].policyChange").doesNotExist())
			.andExpect(jsonPath("$.items[1].content").value("재학증명서"))
			.andExpect(jsonPath("$.items[1].checked").value(true))
			.andExpect(jsonPath("$.items[1].policyChange").value("DROPPED"))
			.andExpect(jsonPath("$.items[1].policyChangeLabel").value("공고에서 빠짐"))
			.andExpect(jsonPath("$.items[2].policyChange").doesNotExist())
			.andExpect(jsonPath("$.items[3].content").value("내가 챙길 것"))
			.andExpect(jsonPath("$.items[3].policyChange").doesNotExist())
			.andExpect(jsonPath("$.items[4].content").value("가족관계증명서"))
			.andExpect(jsonPath("$.items[4].policyChange").value("ADDED"))
			.andExpect(jsonPath("$.items[4].policyChangeLabel").value("새로 추가"));
		mockMvc.perform(get("/api/v1/me/applications").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$[0].documentChanges").value(2));
		// 변경 알림에도 제출 서류 변경이 남는다
		mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.items[0].type").value("CHANGE"));

		// 빠졌던 서류가 다시 들어오면 표시를 뗀다. 같은 내용으로 다시 수집해도 늘지 않는다
		sync("주민등록등본\n재학증명서\n통장 사본\n가족관계증명서");
		sync("주민등록등본\n재학증명서\n통장 사본\n가족관계증명서");
		mockMvc.perform(get("/api/v1/me/applications/{id}", appId).header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.items.length()").value(5))
			.andExpect(jsonPath("$.items[1].policyChange").doesNotExist())
			.andExpect(jsonPath("$.items[4].policyChange").value("ADDED"));
	}

	private void sync(String documents) {
		Map<String, Object> first = new LinkedHashMap<>(items.get(0));
		first.put("sbmsnDcmntCn", documents);
		List<Map<String, Object>> changed = new ArrayList<>(items);
		changed.set(0, first);
		given(apiClient.fetchAll()).willReturn(changed);
		syncService.sync();
	}

	private void check(String owner, Integer appId, Integer itemId) throws Exception {
		mockMvc.perform(patch("/api/v1/me/applications/{id}/items/{itemId}", appId, itemId)
			.header("X-Anonymous-Id", owner)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"checked\":true}")).andExpect(status().isOk());
	}

}
