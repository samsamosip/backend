package com.youthbenefit.common;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.ontong.OntongApiClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** ADMIN_API_KEY 가 있으면 관리자 API 만 잠긴다. (다른 테스트는 키가 없어 열려 있는 상태) */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "spring.docker.compose.enabled=false", "app.admin.api-key=test-admin-key" })
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class AdminKeyIntegrationTest {

	@MockitoBean
	OntongApiClient apiClient;

	@Autowired
	MockMvc mockMvc;

	@Test
	void adminApiNeedsTheKey() throws Exception {
		given(apiClient.fetchAll()).willReturn(List.of());

		mockMvc.perform(post("/api/v1/admin/ontong/sync"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("관리자 키(X-Admin-Key)가 필요합니다."));
		mockMvc.perform(post("/api/v1/admin/ontong/sync").header("X-Admin-Key", "wrong"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/admin/ontong/sync").header("X-Admin-Key", "test-admin-key"))
			.andExpect(status().isOk());

		// 일반 API 는 키 없이 그대로
		mockMvc.perform(get("/api/v1/codes")).andExpect(status().isOk());
	}

}
