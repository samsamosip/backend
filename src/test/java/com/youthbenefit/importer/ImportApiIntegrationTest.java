package com.youthbenefit.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.auth.AccountMergeService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "spring.docker.compose.enabled=false", "importer.base-url=https://importer.example",
		"importer.api-key=test-key", "importer.callback-secret=test-secret",
		"importer.callback-base-url=https://api.example/", "importer.per-hour=5" })
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class ImportApiIntegrationTest {

	private static final String SECRET = "test-secret";

	private static final String LINK = "https://example.go.kr/notice/123";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	AccountMergeService mergeService;

	@MockitoBean
	ImporterClient client;

	private final JsonMapper json = JsonMapper.builder().build();

	private final String me = UUID.randomUUID().toString();

	@BeforeEach
	void setUp() {
		jdbc.execute("delete from import_jobs");
	}

	@Test
	void callbackResultFillsTheInputFields() throws Exception {
		String id = start(me, LINK).andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("QUEUED"))
			.andExpect(jsonPath("$.url").value(LINK))
			.andReturn()
			.getResponse()
			.getContentAsString();
		id = JsonPath.read(id, "$.id");
		verify(client).submitUrl(UUID.fromString(id), LINK,
				"https://api.example/api/v1/internal/import-jobs/" + id + "/result");

		// 콜백 기다리는 30초 안에는 불러오기 서버에 묻지 않는다
		job(me, id).andExpect(jsonPath("$.status").value("QUEUED")).andExpect(jsonPath("$.prefill").doesNotExist());
		verify(client, never()).fetch(any());

		callback(id, """
				{"requestId": "%s", "status": "succeeded", "result": {"title": "OO구 청년 문화패스", "organization": "OO구청",
				 "applyEndDate": "2026-11-20", "applyEndTime": "18:00", "category": "장학금", "memo": "연 20만원 문화비",
				 "sourceUrl": "https://example.go.kr/notice/123"}}
				""".formatted(id)).andExpect(status().isOk());

		job(me, id).andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.prefill.title").value("OO구 청년 문화패스"))
			.andExpect(jsonPath("$.prefill.organization").value("OO구청"))
			.andExpect(jsonPath("$.prefill.applyEndDate").value("2026-11-20"))
			.andExpect(jsonPath("$.prefill.applyEndTime").value("18:00"))
			.andExpect(jsonPath("$.prefill.category").doesNotExist())
			.andExpect(jsonPath("$.prefill.memo").value("연 20만원 문화비"))
			.andExpect(jsonPath("$.prefill.applyUrl").value(LINK))
			.andExpect(jsonPath("$.error").doesNotExist());

		// 같은 작업 콜백이 또 오면 처음 것만 쓰고 200
		callback(id, """
				{"requestId": "%s", "status": "failed", "error": {"code": "INTERNAL", "message": "x"}}
				""".formatted(id)).andExpect(status().isOk());
		job(me, id).andExpect(jsonPath("$.status").value("SUCCEEDED"));
	}

	@Test
	void prefillCanBeRegisteredAsCustomPolicy() throws Exception {
		String id = JsonPath.read(start(me, LINK).andReturn().getResponse().getContentAsString(), "$.id");
		callback(id, """
				{"requestId": "%s", "status": "succeeded", "result": {"title": "청년 월세 지원", "applyEndDate": "2026-12-01",
				 "category": "주거"}}
				""".formatted(id)).andExpect(status().isOk());
		String body = job(me, id).andReturn().getResponse().getContentAsString();
		Object prefill = JsonPath.read(body, "$.prefill");
		mockMvc.perform(post("/api/v1/me/custom-policies").header("X-Anonymous-Id", me)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(prefill)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("청년 월세 지원"))
			.andExpect(jsonPath("$.applyUrl").value(LINK));
	}

	@Test
	void badSignaturesAreRefused() throws Exception {
		String id = JsonPath.read(start(me, LINK).andReturn().getResponse().getContentAsString(), "$.id");
		String body = """
				{"requestId": "%s", "status": "succeeded", "result": {"title": "가짜"}}""".formatted(id);
		String now = String.valueOf(Instant.now().getEpochSecond());

		mockMvc.perform(post("/api/v1/internal/import-jobs/{id}/result", id).contentType(MediaType.APPLICATION_JSON)
			.header("X-Timestamp", now)
			.header("X-Signature", sign("other-secret", now, body))
			.content(body)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("BAD_SIGNATURE"));
		mockMvc.perform(post("/api/v1/internal/import-jobs/{id}/result", id).contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isUnauthorized());
		String old = String.valueOf(Instant.now().minusSeconds(600).getEpochSecond());
		mockMvc.perform(post("/api/v1/internal/import-jobs/{id}/result", id).contentType(MediaType.APPLICATION_JSON)
			.header("X-Timestamp", old)
			.header("X-Signature", sign(SECRET, old, body))
			.content(body)).andExpect(status().isUnauthorized());
		// 서명한 본문과 보낸 본문이 다르면
		mockMvc.perform(post("/api/v1/internal/import-jobs/{id}/result", id).contentType(MediaType.APPLICATION_JSON)
			.header("X-Timestamp", now)
			.header("X-Signature", sign(SECRET, now, body))
			.content(body.replace("가짜", "진짜"))).andExpect(status().isUnauthorized());
		// 주소와 본문의 작업 번호가 다르면
		String other = """
				{"requestId": "%s", "status": "succeeded"}""".formatted(UUID.randomUUID());
		mockMvc.perform(post("/api/v1/internal/import-jobs/{id}/result", id).contentType(MediaType.APPLICATION_JSON)
			.header("X-Timestamp", now)
			.header("X-Signature", sign(SECRET, now, other))
			.content(other)).andExpect(status().isBadRequest());

		job(me, id).andExpect(jsonPath("$.status").value("QUEUED"));
	}

	@Test
	void pollsImporterWhenCallbackIsLate() throws Exception {
		String id = JsonPath.read(start(me, LINK).andReturn().getResponse().getContentAsString(), "$.id");
		age(id, 31);
		when(client.fetch(UUID.fromString(id))).thenReturn(json.readTree("""
				{"requestId": "%s", "status": "failed", "error": {"code": "NOT_A_NOTICE", "message": "공고로 보이는 내용이 없어요"}}
				""".formatted(id)));

		job(me, id).andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.error.code").value("NOT_A_NOTICE"))
			.andExpect(jsonPath("$.error.message").value("공고로 보이는 내용이 없어요"))
			.andExpect(jsonPath("$.prefill").doesNotExist());
	}

	@Test
	void unknownErrorCodeAndRunningTooLongBecomeFriendlyFailures() throws Exception {
		String a = JsonPath.read(start(me, LINK).andReturn().getResponse().getContentAsString(), "$.id");
		age(a, 31);
		when(client.fetch(UUID.fromString(a))).thenReturn(json.readTree("""
				{"requestId": "%s", "status": "failed", "error": {"code": "SOMETHING", "message": ""}}""".formatted(a)));
		job(me, a).andExpect(jsonPath("$.error.code").value("INTERNAL"))
			.andExpect(jsonPath("$.error.message").value("지금은 불러올 수 없어요. 잠시 후 다시 하거나 직접 입력해 주세요."));

		String b = JsonPath.read(start(me, LINK).andReturn().getResponse().getContentAsString(), "$.id");
		age(b, 31);
		when(client.fetch(UUID.fromString(b))).thenReturn(json.readTree("""
				{"requestId": "%s", "status": "running"}""".formatted(b)));
		job(me, b).andExpect(jsonPath("$.status").value("RUNNING"));
		age(b, 200);
		job(me, b).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.error.code").value("TIMEOUT"));
	}

	@Test
	void onlyOwnerSeesJobAndItMovesOnLogin() throws Exception {
		String id = JsonPath.read(start(me, LINK).andReturn().getResponse().getContentAsString(), "$.id");
		String someone = UUID.randomUUID().toString();
		job(someone, id).andExpect(status().isNotFound());

		mergeService.merge(UUID.fromString(me), UUID.fromString(someone));
		job(someone, id).andExpect(status().isOk());
		job(me, id).andExpect(status().isNotFound());
	}

	@Test
	void badLinksAreRefusedBeforeCallingImporter() throws Exception {
		start(me, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.url").exists());
		start(me, "ftp://example.go.kr/a").andExpect(status().isBadRequest());
		start(me, "http://localhost:8080/admin").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.url").value("인터넷에 공개된 공고 주소를 넣어 주세요"));
		start(me, "http://169.254.169.254/latest").andExpect(status().isBadRequest());
		verify(client, never()).submitUrl(any(), anyString(), any());
	}

	@Test
	void importerDownMeansTypeItYourself() throws Exception {
		doThrow(new ImporterException("연결 실패")).when(client).submitUrl(any(), eq(LINK), any());
		start(me, LINK).andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("IMPORT_UNAVAILABLE"));
		assertThat(jdbc.queryForObject("select count(*) from import_jobs", Integer.class)).isZero();
	}

	@Test
	void tooManyImportsAreLimited() throws Exception {
		String someone = UUID.randomUUID().toString();
		for (int i = 0; i < 5; i++) {
			start(someone, LINK).andExpect(status().isAccepted());
		}
		start(someone, LINK).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	private ResultActions start(String owner, String url) throws Exception {
		return mockMvc.perform(post("/api/v1/me/imports").header("X-Anonymous-Id", owner)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(java.util.Map.of("url", url))));
	}

	private ResultActions job(String owner, String id) throws Exception {
		return mockMvc.perform(get("/api/v1/me/imports/{id}", id).header("X-Anonymous-Id", owner));
	}

	private ResultActions callback(String id, String body) throws Exception {
		String now = String.valueOf(Instant.now().getEpochSecond());
		return mockMvc.perform(post("/api/v1/internal/import-jobs/{id}/result", id)
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-Timestamp", now)
			.header("X-Signature", sign(SECRET, now, body))
			.content(body.getBytes(StandardCharsets.UTF_8)));
	}

	/** 작업을 seconds 초 전에 만든 것으로 */
	private void age(String id, int seconds) {
		jdbc.update("update import_jobs set created_at = now() - make_interval(secs => ?), polled_at = null where id = ?::uuid",
				seconds, id);
	}

	private static String sign(String secret, String timestamp, String body) {
		return HexFormat.of()
			.formatHex(ImportJobService.sign(secret, timestamp, body.getBytes(StandardCharsets.UTF_8)));
	}

}
