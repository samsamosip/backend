package com.youthbenefit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.auth.AccountMergeService;
import com.youthbenefit.notification.DailyNotificationService;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class CustomPolicyApiIntegrationTest {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	DailyNotificationService dailyNotifications;

	@Autowired
	AccountMergeService mergeService;

	private final String me = UUID.randomUUID().toString();

	@BeforeEach
	void setUp() {
		jdbc.execute("delete from notifications; delete from notification_settings; delete from policy_changes; "
				+ "delete from checklist_items; delete from applications; delete from user_profiles; "
				+ "delete from policies; delete from sync_cursors");
	}

	@Test
	void registerGoesStraightIntoMyBoxWithChecklistAndDeadline() throws Exception {
		LocalDate end = TODAY.plusDays(3);
		String body = create(me, """
				{"title": " 우리 학교 근로장학 ", "organization": "OO대 학생처", "applyEndDate": "%s", "applyEndTime": "18:00",
				 "category": "교육", "memo": "학생처 2층 제출", "applyUrl": "https://example.ac.kr/notice/1",
				 "documents": ["재학증명서", " 통장 사본 ", "재학증명서"], "status": "PREPARING"}
				""".formatted(end)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.source").value("USER"))
			.andExpect(jsonPath("$.title").value("우리 학교 근로장학"))
			.andExpect(jsonPath("$.status").value("PREPARING"))
			.andExpect(jsonPath("$.deadline.applyEndDate").value(end.toString()))
			.andExpect(jsonPath("$.deadline.applyEndTime").value("18:00"))
			.andExpect(jsonPath("$.deadline.dDay").value(3))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[1].content").value("통장 사본"))
			.andExpect(jsonPath("$.applyUrl").value("https://example.ac.kr/notice/1"))
			.andExpect(jsonPath("$.category").value("교육"))
			.andExpect(jsonPath("$.memo").value("학생처 2층 제출"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer policyId = JsonPath.read(body, "$.policyId");

		mockMvc.perform(get("/api/v1/me/applications").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$[0].source").value("USER"))
			.andExpect(jsonPath("$[0].deadline.dDay").value(3));
		mockMvc.perform(get("/api/v1/policies/{id}", policyId).header("X-Anonymous-Id", me))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memo").value("학생처 2층 제출"));

		// 공개 목록·맞춤에는 안 나온다
		mockMvc.perform(get("/api/v1/policies")).andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/v1/me/matches").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.totalElements").value(0));

		// 마감 3일 전 알림은 온다
		dailyNotifications.run(TODAY, java.time.LocalDateTime.now());
		mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.items[0].type").value("DEADLINE"));
	}

	@Test
	void othersCannotSeeOrAddMyCustomPolicy() throws Exception {
		Integer policyId = JsonPath.read(create(me, "{\"title\":\"내 공고\"}").andReturn().getResponse().getContentAsString(),
				"$.policyId");
		String other = UUID.randomUUID().toString();
		mockMvc.perform(get("/api/v1/policies/{id}", policyId)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/policies/{id}", policyId).header("X-Anonymous-Id", other))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/policies/{id}/eligibility", policyId).header("X-Anonymous-Id", other))
			.andExpect(status().isNotFound());
		json(post("/api/v1/me/applications").header("X-Anonymous-Id", other), "{\"policyId\":" + policyId + "}")
			.andExpect(status().isNotFound());
		json(put("/api/v1/me/custom-policies/{id}", policyId).header("X-Anonymous-Id", other), "{\"title\":\"뺏기\"}")
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/me/custom-policies/{id}", policyId).header("X-Anonymous-Id", other))
			.andExpect(status().isNotFound());
	}

	@Test
	void validation() throws Exception {
		create(me, "{\"title\":\"\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.title").exists());
		create(me, "{\"title\":\"a\",\"category\":\"취미\"}").andExpect(jsonPath("$.errors.category").exists());
		create(me, "{\"title\":\"a\",\"applyUrl\":\"javascript:alert(1)\"}")
			.andExpect(jsonPath("$.errors.applyUrl").exists());
		create(me, "{\"title\":\"a\",\"applyEndTime\":\"18:00\"}")
			.andExpect(jsonPath("$.errors.applyEndTime").exists());
		create(me, "{\"title\":\"a\",\"documents\":[\" \"]}").andExpect(status().isBadRequest());
		create(me, "{\"title\":\"a\",\"applyEndDate\":\"내일\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.applyEndDate").exists());
		// 공고명만 있으면 상시 공고
		create(me, "{\"title\":\"상시\"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.deadline.applyEndDate").doesNotExist())
			.andExpect(jsonPath("$.deadline.dDay").doesNotExist())
			.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void editKeepsChecksAndUserItemsAndDeleteRemovesEverything() throws Exception {
		String body = create(me, "{\"title\":\"수정 전\",\"documents\":[\"A서류\",\"B서류\"]}").andReturn()
			.getResponse()
			.getContentAsString();
		Integer policyId = JsonPath.read(body, "$.policyId");
		Integer appId = JsonPath.read(body, "$.id");
		Integer a = JsonPath.read(body, "$.items[0].id");
		json(patch("/api/v1/me/applications/{id}/items/{itemId}", appId, a).header("X-Anonymous-Id", me),
				"{\"checked\":true}");
		json(post("/api/v1/me/applications/{id}/items", appId).header("X-Anonymous-Id", me), "{\"content\":\"내 항목\"}");

		json(put("/api/v1/me/custom-policies/{id}", policyId).header("X-Anonymous-Id", me),
				"{\"title\":\"수정 후\",\"applyEndDate\":\"" + TODAY.plusDays(10) + "\",\"documents\":[\"A서류\",\"C서류\"]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("수정 후"))
			.andExpect(jsonPath("$.deadline.dDay").value(10))
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].content").value("A서류"))
			.andExpect(jsonPath("$.items[0].checked").value(true))
			.andExpect(jsonPath("$.items[1].content").value("내 항목"))
			.andExpect(jsonPath("$.items[2].content").value("C서류"))
			.andExpect(jsonPath("$.items[2].policyChange").doesNotExist());

		// 준비함에서 빼면 공고째 지워진다
		mockMvc.perform(delete("/api/v1/me/applications/{id}", appId).header("X-Anonymous-Id", me))
			.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from policies", Integer.class)).isZero();

		Integer second = JsonPath.read(create(me, "{\"title\":\"둘째\"}").andReturn().getResponse().getContentAsString(),
				"$.policyId");
		mockMvc.perform(delete("/api/v1/me/custom-policies/{id}", second).header("X-Anonymous-Id", me))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me/applications").header("X-Anonymous-Id", me))
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void deletingMyDataAndLoggingInCarryCustomPolicies() throws Exception {
		create(me, "{\"title\":\"옮길 공고\"}").andExpect(status().isCreated());
		UUID account = UUID.randomUUID();
		mergeService.merge(UUID.fromString(me), account);
		mockMvc.perform(get("/api/v1/me/applications").header("X-Anonymous-Id", account.toString()))
			.andExpect(jsonPath("$[0].title").value("옮길 공고"));
		Integer policyId = jdbc.queryForObject("select id from policies", Integer.class);
		mockMvc.perform(get("/api/v1/policies/{id}", policyId).header("X-Anonymous-Id", account.toString()))
			.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/me").header("X-Anonymous-Id", account.toString()))
			.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from policies", Integer.class)).isZero();
	}

	private ResultActions create(String owner, String body) throws Exception {
		return json(post("/api/v1/me/custom-policies").header("X-Anonymous-Id", owner), body);
	}

	private ResultActions json(MockHttpServletRequestBuilder request, String body) throws Exception {
		return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
	}

}
