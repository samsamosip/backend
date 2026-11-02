package com.youthbenefit.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.auth.AccountMergeService;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class UsageStatsIntegrationTest {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UsageRecorder recorder;

	@Autowired
	AccountMergeService mergeService;

	private final String a = UUID.randomUUID().toString();

	private final String b = UUID.randomUUID().toString();

	@BeforeEach
	void setUp() {
		jdbc.execute("delete from owner_activity; delete from daily_counts; delete from notifications; "
				+ "delete from notification_settings; delete from policy_changes; delete from checklist_items; "
				+ "delete from applications; delete from user_profiles; delete from policies; delete from import_jobs");
		recorder.forget();
	}

	@Test
	void countsVisitorsSearchesAndMyBox() throws Exception {
		mockMvc.perform(put("/api/v1/me/profile").header("X-Anonymous-Id", a)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"birthDate\": \"2002-03-01\"}")).andExpect(status().isOk());
		// 같은 날 여러 번 써도 한 번
		mockMvc.perform(get("/api/v1/me/matches").header("X-Anonymous-Id", a)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me/matches").header("X-Anonymous-Id", b)).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/me/custom-policies").header("X-Anonymous-Id", a)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\": \"학교 근로장학\", \"status\": \"APPLIED\"}")).andExpect(status().isCreated());
		// 검색은 사람 구분 없이도 센다
		mockMvc.perform(get("/api/v1/search").param("q", "월세 지원")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/search").param("q", "취업").header("X-Anonymous-Id", b)).andExpect(status().isOk());
		// a 는 어제도 왔다
		jdbc.update("insert into owner_activity (day, owner_id) values (?, ?::uuid)", TODAY.minusDays(1), a);
		// 공고 목록만 보면 사람을 모르므로 세지 않는다
		mockMvc.perform(get("/api/v1/policies")).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/admin/stats"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.today").value(TODAY.toString()))
			.andExpect(jsonPath("$.visitors.today").value(2))
			.andExpect(jsonPath("$.visitors.last7Days").value(2))
			.andExpect(jsonPath("$.visitors.total").value(2))
			.andExpect(jsonPath("$.visitors.returning").value(1))
			.andExpect(jsonPath("$.profiles").value(1))
			.andExpect(jsonPath("$.applications.people").value(1))
			.andExpect(jsonPath("$.applications.applied").value(1))
			.andExpect(jsonPath("$.applications.customPolicies").value(1))
			.andExpect(jsonPath("$.searches.today").value(2))
			.andExpect(jsonPath("$.searches.total").value(2))
			.andExpect(jsonPath("$.linkImports.total").value(0))
			.andExpect(jsonPath("$.countingSince").value(TODAY.minusDays(1).toString()))
			.andExpect(jsonPath("$.daily.length()").value(14))
			.andExpect(jsonPath("$.daily[13].day").value(TODAY.toString()))
			.andExpect(jsonPath("$.daily[13].visitors").value(2))
			.andExpect(jsonPath("$.daily[13].searches").value(2))
			.andExpect(jsonPath("$.daily[12].visitors").value(1));
	}

	@Test
	void loginMergesVisitsAndDeletingMyDataRemovesThem() throws Exception {
		mockMvc.perform(get("/api/v1/me/matches").header("X-Anonymous-Id", a)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me/matches").header("X-Anonymous-Id", b)).andExpect(status().isOk());

		// 로그인 전 a 와 계정 b 는 한 사람
		mergeService.merge(UUID.fromString(a), UUID.fromString(b));
		assertThat(jdbc.queryForObject("select count(*) from owner_activity", Integer.class)).isEqualTo(1);

		mockMvc.perform(delete("/api/v1/me").header("X-Anonymous-Id", b));
		assertThat(jdbc.queryForObject("select count(*) from owner_activity", Integer.class)).isZero();
	}

}
