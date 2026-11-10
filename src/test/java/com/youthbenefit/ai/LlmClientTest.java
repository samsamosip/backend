package com.youthbenefit.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LlmClientTest {

	@Test
	void configuredModelsAndOptionsReachTheGateway() throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();
		List<JsonNode> requests = new ArrayList<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/completions", exchange -> {
			requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
			byte[] response = """
					{"choices":[{"message":{"content":"{\\"ok\\":true}"}}],"usage":{"total_tokens":12}}
					""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
		try {
			Map<String, Object> config = Map.of("llm.base-url", "http://127.0.0.1:" + server.getAddress().getPort(),
					"llm.api-key", "test-key", "llm.model", "openai.gpt-6-luna", "llm.review-model", "openai.gpt-6.1-sol",
					"llm.temperature", "1", "llm.reasoning-effort", "none");
			LlmProperties properties = new Binder(new MapConfigurationPropertySource(config))
				.bind("llm", Bindable.of(LlmProperties.class)).get();
			LlmClient client = new LlmClient(properties, RestClient.builder(), mapper, Clock.systemUTC());
			Map<String, Object> schema = Map.of("type", "object", "additionalProperties", false, "required", List.of("ok"),
					"properties", Map.of("ok", Map.of("type", "boolean")));
			assertThat(client.json(client.defaultModel(), "Return JSON", "test", "test_schema", schema).path("ok").asBoolean()).isTrue();
			client.json(client.reviewModel(), "Return JSON", "test", "test_schema", schema);
			assertThat(requests).hasSize(2);
			assertThat(requests.get(0).path("model").asString()).isEqualTo("openai.gpt-6-luna");
			assertThat(requests.get(1).path("model").asString()).isEqualTo("openai.gpt-6.1-sol");
			for (JsonNode request : requests) {
				// The former temperature=0 hardcode causes a real 400 with these models.
				assertThat(request.path("temperature").asDouble()).isEqualTo(1);
				assertThat(request.path("reasoning_effort").asString()).isEqualTo("none");
				assertThat(request.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
			}
			assertThat(client.totalTokens()).isEqualTo(24);
		}
		finally {
			server.stop(0);
		}
	}
}
