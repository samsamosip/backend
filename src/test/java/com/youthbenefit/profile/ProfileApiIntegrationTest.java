package com.youthbenefit.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class ProfileApiIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void saveThenReadOnboardingProfile() throws Exception {
		String id = UUID.randomUUID().toString();
		String body = """
				{"birthDate":"2003-06-12","zipCd":"11620","jobCd":"0013003","schoolCd":"0049005",
				 "housingType":"MONTHLY_RENT","sbizCd":[],"interestCategories":["주거","일자리"]}
				""";

		mockMvc.perform(get("/api/v1/me/profile").header("X-Anonymous-Id", id))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("프로필이 아직 없습니다"));

		mockMvc.perform(put("/api/v1/me/profile").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.residenceSido").value("서울"));

		mockMvc.perform(get("/api/v1/me/profile").header("X-Anonymous-Id", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jobCd").value("0013003"))
			.andExpect(jsonPath("$.housingType").value("MONTHLY_RENT"))
			.andExpect(jsonPath("$.sbizCd").isEmpty())
			.andExpect(jsonPath("$.missingFields[*]").value(org.hamcrest.Matchers.hasItem("annualIncome")));

		mockMvc.perform(delete("/api/v1/me").header("X-Anonymous-Id", id)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me/profile").header("X-Anonymous-Id", id)).andExpect(status().isNotFound());
	}

	@Test
	void badInputGivesFieldErrors() throws Exception {
		mockMvc.perform(put("/api/v1/me/profile").header("X-Anonymous-Id", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"zipCd\":\"seoul\",\"jobCd\":\"0013010\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.zipCd").exists());

		mockMvc.perform(put("/api/v1/me/profile").header("X-Anonymous-Id", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"jobCd\":\"0013010\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.jobCd").exists());

		mockMvc.perform(put("/api/v1/me/profile").header("X-Anonymous-Id", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"birthDate\":\"2003/06/12\",\"housingType\":\"HOUSE\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.birthDate").value("날짜는 YYYY-MM-DD 형식이어야 합니다"));

		mockMvc.perform(get("/api/v1/me/profile")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/me/profile").header("X-Anonymous-Id", "not-a-uuid"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void codesListsOnboardingOptions() throws Exception {
		mockMvc.perform(get("/api/v1/codes"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jobCd[2].code").value("0013003"))
			.andExpect(jsonPath("$.jobCd[2].label").value("미취업자"))
			.andExpect(jsonPath("$.housingType[0].code").value("MONTHLY_RENT"))
			.andExpect(jsonPath("$.interestCategories.length()").value(5));
	}

}
