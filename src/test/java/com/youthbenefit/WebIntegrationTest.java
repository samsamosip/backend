package com.youthbenefit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class WebIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void frontendDevServerMayCallApi() throws Exception {
		mockMvc.perform(options("/api/v1/me/profile").header("Origin", "http://localhost:5173")
			.header("Access-Control-Request-Method", "PUT")
			.header("Access-Control-Request-Headers", "X-Anonymous-Id, Content-Type"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void unknownOriginIsRejected() throws Exception {
		mockMvc.perform(options("/api/v1/policies").header("Origin", "https://evil.example")
			.header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
	}

	@Test
	void apiDocsListEndpoints() throws Exception {
		mockMvc.perform(get("/openapi.json"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("청년혜택 길잡이 API"))
			.andExpect(jsonPath("$.paths['/api/v1/me/profile']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/policies']").exists());
		mockMvc.perform(get("/docs")).andExpect(status().is3xxRedirection());
	}

}
