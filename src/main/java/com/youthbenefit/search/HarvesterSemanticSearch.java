package com.youthbenefit.search;

import com.youthbenefit.common.RateLimiter;
import com.youthbenefit.harvester.HarvesterProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Fast, bounded school-notice retrieval, separate from the archival sync client's long retries. */
@Slf4j
@Component
public class HarvesterSemanticSearch {

	public record Hit(String sourceId, double score) {
	}

	public record Result(boolean used, List<Hit> hits) {
		static final Result NONE = new Result(false, List.of());
	}

	private record Cached(Instant expiresAt, Result result) {
	}

	private final HarvesterProperties harvester;
	private final SemanticSearchProperties properties;
	private final Clock clock;
	private final RestClient client;
	private final RateLimiter rateLimit;
	private final Map<String, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<>(64, .75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
			return size() > 500;
		}
	});

	public HarvesterSemanticSearch(HarvesterProperties harvester, SemanticSearchProperties properties,
			RestClient.Builder builder, Clock clock) {
		this.harvester = harvester;
		this.properties = properties;
		this.clock = clock;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofMillis(Math.min(2000, Math.max(100, properties.timeoutMillis()))));
		factory.setReadTimeout(Duration.ofMillis(Math.max(100, properties.timeoutMillis())));
		this.client = builder.clone().requestFactory(factory)
			.baseUrl(harvester.baseUrl() == null ? "" : harvester.baseUrl())
			.defaultHeader("X-API-Key", harvester.apiKey() == null ? "" : harvester.apiKey()).build();
		this.rateLimit = new RateLimiter(properties.requestsPerMinute(), Duration.ofMinutes(1), clock);
	}

	public Result search(SearchQuery query) {
		if (!properties.enabled() || harvester.baseUrl() == null || harvester.baseUrl().isBlank()
				|| harvester.apiKey() == null || harvester.apiKey().isBlank() || query.keywords().isEmpty()
				|| !query.categories().isEmpty() && !query.categories().contains("교육")) {
			return Result.NONE;
		}
		// Use the remaining benefit concepts after chip removal. No saved profile or deleted filters are sent.
		String text = query.keywords().stream().filter(g -> !g.isEmpty()).map(List::getFirst)
			.collect(java.util.stream.Collectors.joining(" "));
		if (text.isBlank()) return Result.NONE;
		Cached cached = cache.get(text);
		if (cached != null && cached.expiresAt().isAfter(clock.instant())) return cached.result();
		if (!rateLimit.tryAcquire("harvester-search")) return Result.NONE;
		try {
			JsonNode body = client.get().uri(uri -> uri.path("/v1/opportunities")
				.queryParam("q", text).queryParam("search_mode", "hybrid").queryParam("status", "active")
				.queryParam("include_stale", true).queryParam("limit", Math.clamp(properties.candidateLimit(), 1, 100)).build())
				.retrieve().body(JsonNode.class);
			if (body == null || !body.path("items").isArray() || !body.path("query").path("vector_used").asBoolean(false)) {
				return remember(text, Result.NONE, 15);
			}
			Map<String, Hit> hits = new LinkedHashMap<>();
			for (JsonNode item : body.path("items")) {
				String id = item.path("opportunity_id").asString("");
				double score = item.path("vector_score").asDouble(Double.NaN);
				if (!id.isBlank() && Double.isFinite(score) && score >= properties.minScore() && score <= 1) {
					hits.merge(id, new Hit(id, score), (a, b) -> a.score() >= b.score() ? a : b);
				}
			}
			return remember(text, new Result(true, List.copyOf(hits.values())), Math.max(1, properties.cacheSeconds()));
		}
		catch (RuntimeException ex) {
			// No retries/sleeps on the user's search path, including 429 and timeouts.
			log.warn("학교 장학 의미 검색 실패, 기존 검색 유지 ({})", ex.getClass().getSimpleName());
			return remember(text, Result.NONE, 15);
		}
	}

	private Result remember(String text, Result result, int seconds) {
		cache.put(text, new Cached(clock.instant().plusSeconds(seconds), result));
		return result;
	}
}
