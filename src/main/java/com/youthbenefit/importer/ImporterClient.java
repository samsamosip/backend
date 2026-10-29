package com.youthbenefit.importer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 링크 불러오기 서버(AI 팀원) 호출. 모든 요청에 X-API-Key 헤더. 작업 만들기는 같은 requestId 로 다시 보내도 같은 작업이라 연결이
 * 끊기면 다시 보낸다.
 */
@Component
public class ImporterClient {

	private static final List<Duration> RETRY_DELAYS = List.of(Duration.ofSeconds(1), Duration.ofSeconds(3));

	private final ImporterProperties properties;

	private final RestClient restClient;

	private final JsonMapper jsonMapper;

	public ImporterClient(ImporterProperties properties, RestClient.Builder restClientBuilder, JsonMapper jsonMapper) {
		this.properties = properties;
		this.restClient = restClientBuilder.clone()
			.requestFactory(requestFactory())
			.baseUrl(properties.baseUrl() == null ? "" : properties.baseUrl())
			.defaultHeader("X-API-Key", properties.apiKey() == null ? "" : properties.apiKey())
			.build();
		this.jsonMapper = jsonMapper;
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(15));
		return factory;
	}

	/** 링크 불러오기 작업 만들기. callbackUrl 이 null 이면 보내지 않는다(로컬 — 조회만). 불러오기 서버는 바로 202 로 답한다. */
	public void submitUrl(UUID requestId, String url, String callbackUrl) {
		checkConfigured();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("requestId", requestId.toString());
		body.put("type", "url");
		body.put("url", url);
		if (callbackUrl != null) {
			body.put("callbackUrl", callbackUrl);
		}
		request("작업 만들기", () -> restClient.post()
			.uri("/v1/import-jobs")
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(String.class));
	}

	/** 작업 결과 직접 조회. 콜백과 같은 모양. 없는 작업(24시간 지나 지워짐 등)이면 null. */
	public JsonNode fetch(UUID requestId) {
		checkConfigured();
		try {
			String body = restClient.get().uri("/v1/import-jobs/{id}", requestId).retrieve().body(String.class);
			return jsonMapper.readTree(body);
		}
		catch (HttpClientErrorException.NotFound ex) {
			return null;
		}
		catch (RestClientException ex) {
			throw new ImporterException("불러오기 서버 조회 실패: " + ex.getMessage(), ex);
		}
	}

	private void checkConfigured() {
		if (!properties.configured()) {
			throw new ImporterException("IMPORTER_BASE_URL 또는 IMPORTER_API_KEY 가 설정되지 않았습니다. .env 파일을 확인하세요.");
		}
	}

	private void request(String what, Supplier<String> call) {
		int failures = 0;
		while (true) {
			try {
				call.get();
				return;
			}
			catch (HttpClientErrorException ex) {
				// 400(형식)·401(키)·413(크기) 은 다시 보내도 같다
				throw new ImporterException("불러오기 서버 오류 " + ex.getStatusCode().value() + " (" + what + ")", ex);
			}
			catch (RestClientException ex) {
				if (failures >= RETRY_DELAYS.size()) {
					throw new ImporterException("불러오기 서버 호출 실패 (" + what + "): " + ex.getMessage(), ex);
				}
				sleep(RETRY_DELAYS.get(failures++));
			}
		}
	}

	private static void sleep(Duration delay) {
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ImporterException("불러오기 서버 재시도 중 중단됨", ex);
		}
	}

}
