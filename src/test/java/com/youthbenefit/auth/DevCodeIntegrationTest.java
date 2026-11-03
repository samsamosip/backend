package com.youthbenefit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;

/** 개발용 서버(메일 없음 + AUTH_DEV_SHOW_CODE): 인증 코드를 응답으로 받아 가입·비밀번호 찾기를 끝까지 할 수 있다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "spring.docker.compose.enabled=false", "app.auth.mail.username=",
		"app.auth.dev-show-code=true" })
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class DevCodeIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc.execute("delete from refresh_tokens; delete from verification_codes; delete from users");
	}

	@Test
	void signupAndResetWorkWithCodeFromResponse() throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"dev@example.com\",\"password\":\"Testpass1234\",\"nickname\":\"개발\"}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.devCode").isString())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String code = JsonPath.read(body, "$.devCode");
		assertThat(code).matches("\\d{6}");

		mockMvc.perform(post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"dev@example.com\",\"code\":\"" + code + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isString());

		mockMvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"dev@example.com\"}")).andExpect(status().isAccepted()).andExpect(jsonPath("$.devCode").isString());
	}

}
