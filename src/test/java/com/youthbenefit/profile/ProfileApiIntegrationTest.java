package com.youthbenefit.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
			.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.residenceSido").value("서울"))
			.andExpect(jsonPath("$.residenceName").value("서울 관악구"));

		mockMvc.perform(get("/api/v1/regions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(16))
			.andExpect(jsonPath("$[0].name").value("서울"))
			.andExpect(jsonPath("$[0].sigungu[?(@.code == '11620')].label").value("관악구"));

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
			.content("{\"zipCd\":\"99999\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.zipCd").value("알 수 없는 시군구 코드: 99999"));

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

	@Test
	void patchChangesOnlySentFields() throws Exception {
		String id = UUID.randomUUID().toString();

		// 프로필이 없어도 PATCH 로 시작할 수 있다 (WF03 에서 부족한 정보 하나 입력)
		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"birthDate\":\"2003-06-12\",\"zipCd\":\"11620\",\"sbizCd\":[]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.residenceSido").value("서울"));

		// 보낸 항목만 바뀌고 나머지는 그대로
		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"annualIncome\":2400}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.annualIncome").value(2400))
			.andExpect(jsonPath("$.birthDate").value("2003-06-12"))
			.andExpect(jsonPath("$.zipCd").value("11620"))
			.andExpect(jsonPath("$.sbizCd").isEmpty());

		// null 은 "모름"으로 되돌린다
		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"zipCd\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zipCd").doesNotExist())
			.andExpect(jsonPath("$.annualIncome").value(2400))
			.andExpect(jsonPath("$.missingFields[*]").value(org.hamcrest.Matchers.hasItem("zipCd")));
	}

	@Test
	void patchRejectsBadInput() throws Exception {
		String id = UUID.randomUUID().toString();

		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"income\":2400}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.income").value("알 수 없는 항목입니다"));

		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"zipCd\":\"seoul\",\"jobCd\":\"0013010\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.zipCd").value("시군구 코드 5자리여야 합니다"));

		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"jobCd\":\"0013010\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.jobCd").exists());

		mockMvc.perform(patch("/api/v1/me/profile").header("X-Anonymous-Id", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"annualIncome\":\"많음\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.annualIncome").value("숫자여야 합니다"));
	}

}
