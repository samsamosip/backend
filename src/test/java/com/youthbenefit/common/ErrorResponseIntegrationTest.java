package com.youthbenefit.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.youthbenefit.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 오류는 어떤 경우든 한국어 message + errors 모양. Spring 기본 영어 응답이 나오지 않는다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class ErrorResponseIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	private final String owner = UUID.randomUUID().toString();

	@Test
	void wrongAddressMethodAndFormatAreKorean() throws Exception {
		mockMvc.perform(get("/api/v1/nope"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("없는 주소예요. API 문서(/docs)에서 주소를 확인해 주세요."));
		mockMvc.perform(delete("/api/v1/policies/1"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.message").value("DELETE 방식은 이 주소에서 쓸 수 없어요."));
		mockMvc.perform(post("/api/v1/me/applications").header("X-Anonymous-Id", owner)
			.contentType(MediaType.TEXT_PLAIN)
			.content("hi"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.message").value("보내는 형식(Content-Type)을 확인해 주세요."));
		mockMvc.perform(multipart("/api/v1/me/imports/image").header("X-Anonymous-Id", owner))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.image").value("사진을 올려 주세요"));
	}

	@Test
	void inputChecksAreKorean() throws Exception {
		mockMvc.perform(put("/api/v1/me/profile").header("X-Anonymous-Id", owner)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"birthDate\": \"2099-01-01\", \"annualIncome\": -5, \"gradeLevel\": 9}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.birthDate").value("오늘 이전 날짜여야 합니다"))
			.andExpect(jsonPath("$.errors.annualIncome").value("0 이상이어야 합니다"))
			.andExpect(jsonPath("$.errors.gradeLevel").value("1~6 사이여야 합니다"));
	}

}
