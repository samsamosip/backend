package com.youthbenefit.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정해 둔 JSON 모양(json_schema, strict)으로만 답을 받는다. 서버 오류·시간 초과면 2초 뒤 한 번 더 시도한다. 키·주소는 .env 의
 * PWC_LLM_*.
 */
@Slf4j
@Component
public class LlmClient {

	private final LlmProperties properties;

	private final RestClient restClient;

	private final JsonMapper jsonMapper;

	private final AtomicLong totalTokens = new AtomicLong();

	public LlmClient(LlmProperties properties, RestClient.Builder builder, JsonMapper jsonMapper) {
		this.properties = properties;
		this.jsonMapper = jsonMapper;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds() > 0 ? properties.timeoutSeconds() : 90));
		this.restClient = builder.clone().requestFactory(factory).build();
	}

	public boolean configured() {
		return properties.configured();
	}

	public String defaultModel() {
		return properties.model();
	}

	public String reviewModel() {
		return properties.reviewModel();
	}

	/** 이 서버가 켜진 뒤 쓴 토큰 수 (비용 확인용 로그) */
	public long totalTokens() {
		return totalTokens.get();
	}

	/**
	 * @param schema JSON Schema (strict: 모든 칸 required, additionalProperties false)
	 * @return 모델이 낸 JSON
	 */
	public JsonNode json(String model, String system, String user, String schemaName, Map<String, Object> schema) {
		if (!configured()) {
			throw new LlmException("LLM 이 설정되지 않았어요 (PWC_LLM_BASE_URL, PWC_LLM_API_KEY)");
		}
		Map<String, Object> body = Map.of("model", model, "temperature", 0,
				"messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)),
				"response_format", Map.of("type", "json_schema", "json_schema",
						Map.of("name", schemaName, "strict", true, "schema", schema)));
		for (int attempt = 1;; attempt++) {
			try {
				JsonNode response = restClient.post()
					.uri(properties.baseUrl().replaceAll("/+$", "") + "/v1/chat/completions")
					.header("Authorization", "Bearer " + properties.apiKey())
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(JsonNode.class);
				if (response == null) {
					throw new LlmException("빈 응답");
				}
				totalTokens.addAndGet(response.path("usage").path("total_tokens").asLong(0));
				String content = response.path("choices").path(0).path("message").path("content").asString(null);
				if (content == null) {
					throw new LlmException("응답에 내용이 없음");
				}
				return jsonMapper.readTree(content);
			}
			catch (HttpClientErrorException ex) {
				// 4xx(모델 이름·키·한도 문제)는 다시 해도 같다
				throw new LlmException("LLM 요청 거절 " + ex.getStatusCode().value() + ": "
						+ ex.getResponseBodyAsString().substring(0, Math.min(200, ex.getResponseBodyAsString().length())), ex);
			}
			catch (RestClientException | LlmException ex) {
				if (attempt >= 2) {
					throw ex instanceof LlmException le ? le : new LlmException("LLM 호출 실패: " + ex.getMessage(), ex);
				}
				log.warn("LLM 호출 실패, 다시 시도: {}", ex.getMessage());
				sleep();
			}
		}
	}

	private static void sleep() {
		try {
			Thread.sleep(2000);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new LlmException("중단됨", ex);
		}
	}

}
