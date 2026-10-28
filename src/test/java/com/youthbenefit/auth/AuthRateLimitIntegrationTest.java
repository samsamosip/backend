package com.youthbenefit.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.youthbenefit.TestcontainersConfiguration;
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

/** 가입·로그인 남용 막기: 한 사람(IP) 메일 요청·로그인 시도 수, 한 주소 하루 코드 수 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "spring.docker.compose.enabled=false", "app.auth.rate-limit.mail-per-hour=3",
		"app.auth.rate-limit.attempts-per-10-minutes=4" })
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class AuthRateLimitIntegrationTest {

	@MockitoBean
	AuthMailer mailer;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		jdbc.execute("delete from refresh_tokens; delete from verification_codes; delete from users");
	}

	@Test
	void mailRequestsAndLoginAttemptsArePerIp() throws Exception {
		// 메일 요청 1시간 3번 (주소가 달라도 같은 사람)
		for (int i = 0; i < 3; i++) {
			signup("bomb" + i + "@example.com", "10.0.0.1").andExpect(status().isAccepted());
		}
		signup("bomb9@example.com", "10.0.0.1").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("MAIL_RATE_LIMITED"));
		// 다른 사람은 따로
		signup("other@example.com", "10.0.0.2").andExpect(status().isAccepted());

		// 로그인 시도 10분 4번
		for (int i = 0; i < 4; i++) {
			login("nobody@example.com", "10.0.0.3").andExpect(status().isUnauthorized());
		}
		login("nobody@example.com", "10.0.0.3").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
	}

	@Test
	void oneAddressGetsAtMostTenCodesADay() throws Exception {
		// 1분 제한·IP 제한을 피해 하루에 이미 10통 보낸 상태를 만든다
		for (int i = 0; i < 10; i++) {
			jdbc.update("insert into verification_codes (email, purpose, code_hash, expires_at, attempts, created_at) "
					+ "values ('victim@example.com', 'SIGNUP', ?, now() + interval '10 minutes', 0, now() - interval '"
					+ (2 + i) + " minutes')", "h" + i);
		}
		signup("victim@example.com", "10.0.0.9").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("CODE_DAILY_LIMIT"));
	}

	private ResultActions signup(String email, String ip) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/signup").with(r -> {
			r.setRemoteAddr(ip);
			return r;
		})
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"good1pass\",\"nickname\":\"테스트\"}"));
	}

	private ResultActions login(String email, String ip) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").with(r -> {
			r.setRemoteAddr(ip);
			return r;
		}).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"password\":\"good1pass\"}"));
	}

}
