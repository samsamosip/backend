package com.youthbenefit.ontong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class OntongApiClientTest {

	private static final String BASE_URL = "https://www.youthcenter.go.kr";

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private OntongApiClient client(String apiKey) {
		OntongProperties properties = new OntongProperties(BASE_URL, apiKey, 2, null);
		return new OntongApiClient(properties, builder, JsonMapper.builder().build(), List.of(Duration.ZERO));
	}

	@Test
	void fetchesEveryPageUntilTotalCount() throws IOException {
		OntongApiClient client = client("test-key");
		server.expect(requestTo(startsWith(BASE_URL + "/go/ythip/getPlcy")))
			.andExpect(queryParam("apiKeyNm", "test-key"))
			.andExpect(queryParam("pageNum", "1"))
			.andExpect(queryParam("rtnType", "json"))
			.andRespond(withSuccess(fixture("ontong-page1.json"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(startsWith(BASE_URL + "/go/ythip/getPlcy")))
			.andExpect(queryParam("pageNum", "2"))
			.andRespond(withSuccess(fixture("ontong-page2.json"), MediaType.APPLICATION_JSON));

		List<Map<String, Object>> items = client.fetchAll();

		assertThat(items).hasSize(3);
		assertThat(items).extracting(item -> item.get("aplyPrdSeCd"))
			.containsExactly("0057001", "0057002", "0057003");
		server.verify();
	}

	@Test
	void retriesOnceWhenServerFails() throws IOException {
		OntongApiClient client = client("test-key");
		server.expect(requestTo(startsWith(BASE_URL))).andRespond(withServerError());
		server.expect(requestTo(startsWith(BASE_URL)))
			.andRespond(withSuccess(fixture("ontong-response.json"), MediaType.APPLICATION_JSON));

		assertThat(client.fetchAll()).hasSize(4);
		server.verify();
	}

	@Test
	void reportsCauseAfterAllRetriesFail() {
		OntongApiClient client = client("test-key");
		server.expect(requestTo(startsWith(BASE_URL))).andRespond(withServerError());
		server.expect(requestTo(startsWith(BASE_URL))).andRespond(withServerError());

		assertThatThrownBy(client::fetchAll).isInstanceOf(OntongApiException.class)
			.hasMessageContaining("2번 시도")
			.hasMessageContaining("500");
	}

	@Test
	void invalidKeyXmlResponseIsReportedClearly() {
		OntongApiClient client = client("wrong-key");
		server.expect(requestTo(startsWith(BASE_URL)))
			.andRespond(withSuccess(
					"<HashMap><errorCode>e001</errorCode><errorMsg>invalid api key.</errorMsg></HashMap>",
					MediaType.TEXT_XML));

		assertThatThrownBy(client::fetchAll).isInstanceOf(OntongApiException.class)
			.hasMessageContaining("invalid api key");
	}

	@Test
	void missingKeyFailsBeforeCallingApi() {
		assertThatThrownBy(() -> client(" ").fetchAll()).isInstanceOf(OntongApiException.class)
			.hasMessageContaining("ONTONG_API_KEY");
	}

	private String fixture(String name) throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
