package com.youthbenefit.harvester;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * policy-harvester(학교 장학 수집기) API 호출. 모든 요청에 X-API-Key 헤더가 필요하다.
 * 키마다 분당 요청 한도(실측 약 60회)가 있어서 429 가 오면 Retry-After 만큼 기다렸다가 다시 보낸다.
 */
@Component
public class HarvesterApiClient {

	private static final int PAGE_SIZE = 100;

	private static final int CHANGES_PAGE_SIZE = 500;

	private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
	};

	private static final int MAX_RATE_LIMIT_WAITS = 10;

	private static final List<Duration> RETRY_DELAYS = List.of(Duration.ofSeconds(2), Duration.ofSeconds(5));

	private final HarvesterProperties properties;

	private final RestClient restClient;

	private final JsonMapper jsonMapper;

	public HarvesterApiClient(HarvesterProperties properties, RestClient.Builder restClientBuilder,
			JsonMapper jsonMapper) {
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
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(60));
		return factory;
	}

	/** 장학 공고 목록 전체 (요약). cursor 로 끝까지 넘긴다. */
	public List<Map<String, Object>> listAll() {
		checkConfigured();
		List<Map<String, Object>> all = new ArrayList<>();
		String cursor = null;
		do {
			String c = cursor;
			String body = request("목록", () -> restClient.get()
				.uri(uri -> {
					uri.path("/v1/opportunities").queryParam("limit", PAGE_SIZE);
					if (c != null) {
						uri.queryParam("cursor", c);
					}
					return uri.build();
				})
				.retrieve()
				.body(String.class));
			JsonNode page = jsonMapper.readTree(body);
			for (JsonNode item : page.path("items")) {
				all.add(jsonMapper.convertValue(item, MAP));
			}
			JsonNode next = page.path("next_cursor");
			cursor = next.isString() ? next.asString() : null;
		}
		while (cursor != null && !cursor.isBlank());
		return all;
	}

	/** 변경 목록 한 쪽. items 는 event_seq 순서, nextCursor 는 마지막으로 받은 event_seq (없으면 보낸 cursor 그대로). */
	public record ChangesPage(List<Map<String, Object>> items, String nextCursor) {
	}

	/** cursor 다음에 생긴 변경(생성·수정·합쳐짐·비공개 등). */
	public ChangesPage changes(String cursor) {
		checkConfigured();
		String body = request("변경 목록", () -> restClient.get()
			.uri(uri -> uri.path("/v1/changes").queryParam("cursor", cursor).queryParam("limit", CHANGES_PAGE_SIZE).build())
			.retrieve()
			.body(String.class));
		JsonNode page = jsonMapper.readTree(body);
		List<Map<String, Object>> items = new ArrayList<>();
		for (JsonNode item : page.path("items")) {
			items.add(jsonMapper.convertValue(item, MAP));
		}
		JsonNode next = page.path("next_cursor");
		return new ChangesPage(items, next.isMissingNode() || next.isNull() ? cursor : next.asString());
	}

	/** 공고 하나의 상세 (자격·기간·서류). 원본 JSON 문자열 그대로 돌려준다. */
	public String detail(String opportunityId) {
		checkConfigured();
		return request("상세 " + opportunityId, () -> restClient.get()
			.uri("/v1/opportunities/{id}", opportunityId)
			.retrieve()
			.body(String.class));
	}

	private void checkConfigured() {
		if (properties.baseUrl() == null || properties.baseUrl().isBlank() || properties.apiKey() == null
				|| properties.apiKey().isBlank()) {
			throw new HarvesterApiException("HARVESTER_BASE_URL 또는 HARVESTER_API_KEY 가 설정되지 않았습니다. .env 파일을 확인하세요.");
		}
	}

	private String request(String what, Supplier<String> call) {
		int rateLimitWaits = 0;
		int failures = 0;
		while (true) {
			try {
				return call.get();
			}
			catch (HttpClientErrorException.TooManyRequests ex) {
				if (++rateLimitWaits > MAX_RATE_LIMIT_WAITS) {
					throw new HarvesterApiException("Harvester 요청 한도 초과가 계속됩니다 (" + what + ")", ex);
				}
				sleep(retryAfter(ex));
			}
			catch (HttpClientErrorException ex) {
				// 401(키 오류), 404 등은 다시 보내도 같다
				throw new HarvesterApiException("Harvester API 오류 " + ex.getStatusCode().value() + " (" + what + ")", ex);
			}
			catch (RestClientException ex) {
				if (failures >= RETRY_DELAYS.size()) {
					throw new HarvesterApiException("Harvester API 호출 실패 (" + what + "): " + ex.getMessage(), ex);
				}
				sleep(RETRY_DELAYS.get(failures++));
			}
		}
	}

	private static Duration retryAfter(HttpClientErrorException ex) {
		String header = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
		try {
			return Duration.ofSeconds(header == null ? 10 : Math.max(1, Long.parseLong(header.strip())));
		}
		catch (NumberFormatException e) {
			return Duration.ofSeconds(10);
		}
	}

	private static void sleep(Duration delay) {
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new HarvesterApiException("Harvester API 재시도 중 중단됨", ex);
		}
	}

}
