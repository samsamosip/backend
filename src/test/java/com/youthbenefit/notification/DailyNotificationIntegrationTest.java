package com.youthbenefit.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.ontong.OntongApiClient;
import com.youthbenefit.ontong.OntongResponse;
import com.youthbenefit.ontong.OntongSyncService;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

/** 매일 알림: 새 맞춤 공고 묶음, 마감 임박, 알림 설정. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "spring.docker.compose.enabled=false", "notifications.daily.enabled=false" })
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class DailyNotificationIntegrationTest {

	@MockitoBean
	OntongApiClient apiClient;

	@Autowired
	OntongSyncService syncService;

	@Autowired
	DailyNotificationService dailyService;

	@Autowired
	PolicyRepository policyRepository;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MockMvc mockMvc;

	private List<Map<String, Object>> items;

	@BeforeEach
	void setUp() throws Exception {
		jdbcTemplate.execute("delete from notifications; delete from policy_changes; delete from checklist_items; "
				+ "delete from applications; delete from notification_settings; delete from user_profiles; "
				+ "delete from sync_cursors; delete from policies");
		try (InputStream in = getClass().getResourceAsStream("/fixtures/ontong-response.json")) {
			items = JsonMapper.builder().build().readValue(in, OntongResponse.class).result().youthPolicyList();
		}
		given(apiClient.fetchAll()).willReturn(items);
		syncService.sync();
	}

	private String profile(String body) throws Exception {
		String id = UUID.randomUUID().toString();
		mockMvc.perform(put("/api/v1/me/profile").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isOk());
		return id;
	}

	private static LocalDate today() {
		return LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
	}

	@Test
	void newMatchingPoliciesAreBundledOncePerDay() throws Exception {
		// 세종 거주 재직자 → 고용환경개선사업(세종·19~39세·재직자) 은 조건 일치
		String worker = profile("{\"birthDate\":\"2000-05-01\",\"zipCd\":\"36110\",\"jobCd\":\"0013001\"}");
		String muted = profile("{\"birthDate\":\"2000-05-01\",\"zipCd\":\"36110\",\"jobCd\":\"0013001\"}");
		mockMvc.perform(put("/api/v1/me/notification-settings").header("X-Anonymous-Id", muted)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"newMatches\":false,\"newMatchVerdicts\":[\"MATCH\"],\"deadline\":true,\"deadlineDays\":[3],\"changes\":true}"))
			.andExpect(status().isOk());

		// 처음 실행은 기준 시점만 잡는다 — 이미 있던 공고는 "새 공고"가 아니다
		assertThat(dailyService.run(today(), LocalDateTime.now()).newMatchNotifications()).isZero();

		// 새 공고 하나가 들어온다
		Map<String, Object> fresh = new LinkedHashMap<>(items.get(3));
		fresh.put("plcyNo", "20261024005400199999");
		fresh.put("plcyNm", "세종 청년 재직자 지원(새 공고)");
		List<Map<String, Object>> withFresh = new ArrayList<>(items);
		withFresh.add(fresh);
		given(apiClient.fetchAll()).willReturn(withFresh);
		syncService.sync();

		DailyNotificationService.Result result = dailyService.run(today(), LocalDateTime.now());
		assertThat(result.newPolicies()).isEqualTo(1);
		assertThat(result.newMatchNotifications()).isEqualTo(1);

		mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", worker).param("type", "NEW_MATCHES"))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].label").value("새 공고"))
			.andExpect(jsonPath("$.items[0].summary").value("내 조건에 맞는 새 공고 1건"));
		Long id = notificationRepository.findAll().getFirst().getId();
		mockMvc.perform(get("/api/v1/me/notifications/{id}", id).header("X-Anonymous-Id", worker))
			.andExpect(jsonPath("$.newPolicies.policies[0].title").value("세종 청년 재직자 지원(새 공고)"))
			.andExpect(jsonPath("$.newPolicies.policies[0].verdict").value("MATCH"));

		// 끈 사람은 받지 않고, 같은 날 다시 돌려도 늘지 않는다
		mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", muted))
			.andExpect(jsonPath("$.items.length()").value(0));
		assertThat(dailyService.run(today(), LocalDateTime.now()).newMatchNotifications()).isZero();
	}

	@Test
	void deadlineRemindersFollowSettingsAndSkipApplied() throws Exception {
		// 고용환경개선사업 마감 2026-12-31. "오늘"을 12/28 로 두면 3일 전
		Long policyId = policyRepository.findAll()
			.stream()
			.filter(p -> p.getTitle().equals("고용환경개선사업"))
			.map(Policy::getId)
			.findFirst()
			.orElseThrow();
		String waiting = UUID.randomUUID().toString();
		String applied = UUID.randomUUID().toString();
		String onlyOneDay = UUID.randomUUID().toString();
		for (String owner : List.of(waiting, applied, onlyOneDay)) {
			mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"policyId\":" + policyId + (owner.equals(applied) ? ",\"status\":\"APPLIED\"" : "") + "}"))
				.andExpect(status().isCreated());
		}
		mockMvc.perform(put("/api/v1/me/notification-settings").header("X-Anonymous-Id", onlyOneDay)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"newMatches\":true,\"newMatchVerdicts\":[\"MATCH\",\"NEEDS_CHECK\"],\"deadline\":true,\"deadlineDays\":[1],\"changes\":true}"))
			.andExpect(status().isOk());

		assertThat(dailyService.run(LocalDate.of(2026, 12, 28), LocalDateTime.now()).deadlineNotifications())
			.isEqualTo(1);
		assertThat(dailyService.run(LocalDate.of(2026, 12, 28), LocalDateTime.now()).deadlineNotifications()).isZero();

		mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", waiting))
			.andExpect(jsonPath("$.items[0].type").value("DEADLINE"))
			.andExpect(jsonPath("$.items[0].label").value("마감 임박"))
			.andExpect(jsonPath("$.items[0].policyTitle").value("고용환경개선사업"))
			.andExpect(jsonPath("$.items[0].summary").value("마감 3일 전이에요 (12/31 마감)"));
		mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", applied))
			.andExpect(jsonPath("$.items.length()").value(0));

		// 12/30(하루 전): 기본 설정(7·3·1·당일)인 사람과 하루 전만 받기로 한 사람 둘 다 받는다
		assertThat(dailyService.run(LocalDate.of(2026, 12, 30), LocalDateTime.now()).deadlineNotifications())
			.isEqualTo(2);
		mockMvc.perform(get("/api/v1/me/notifications").header("X-Anonymous-Id", onlyOneDay))
			.andExpect(jsonPath("$.items[0].summary").value("마감 1일 전이에요 (12/31 마감)"));
	}

	@Test
	void settingsHaveDefaultsAndAreValidated() throws Exception {
		String id = UUID.randomUUID().toString();
		mockMvc.perform(get("/api/v1/me/notification-settings").header("X-Anonymous-Id", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.newMatches").value(true))
			.andExpect(jsonPath("$.deadlineDays").value(org.hamcrest.Matchers.contains(7, 3, 1, 0)))
			.andExpect(jsonPath("$.changes").value(true));

		mockMvc.perform(put("/api/v1/me/notification-settings").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"newMatches\":true,\"newMatchVerdicts\":[\"NO_MATCH\"],\"deadline\":true,\"deadlineDays\":[99],\"changes\":true,\"quietStart\":\"22:00\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.newMatchVerdicts").exists())
			.andExpect(jsonPath("$.errors.deadlineDays").exists())
			.andExpect(jsonPath("$.errors.quietStart").exists());

		mockMvc.perform(put("/api/v1/me/notification-settings").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"newMatches\":true,\"newMatchVerdicts\":[\"MATCH\"],\"deadline\":true,\"deadlineDays\":[1,3],\"changes\":false,\"quietStart\":\"22:00\",\"quietEnd\":\"08:00\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deadlineDays").value(org.hamcrest.Matchers.contains(3, 1)))
			.andExpect(jsonPath("$.changes").value(false))
			.andExpect(jsonPath("$.quietStart").value("22:00:00"));
	}

}
