package com.youthbenefit.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.youthbenefit.harvester.HarvesterProperties;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HarvesterSemanticSearchTest {
	HttpServer server;
	List<String> requests = new ArrayList<>();
	String body = """
			{"query":{"vector_used":true},"items":[
			{"opportunity_id":"valid","vector_score":0.55},
			{"opportunity_id":"valid","vector_score":0.65},
			{"opportunity_id":"weak","vector_score":0.2},
			{"opportunity_id":"missing-score"}]}
			""";
	int status = 200;
	int delayMillis;

	@BeforeEach
	void setUp() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/opportunities", exchange -> {
			requests.add(java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
			assertThat(exchange.getRequestHeaders().getFirst("X-API-Key")).isEqualTo("test-key");
			if (delayMillis > 0) {
				try { Thread.sleep(delayMillis); }
				catch (InterruptedException e) { Thread.currentThread().interrupt(); }
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void tearDown() { server.stop(0); }

	HarvesterSemanticSearch client() {
		return new HarvesterSemanticSearch(new HarvesterProperties("http://127.0.0.1:" + server.getAddress().getPort(), "test-key", null),
			new SemanticSearchProperties(true, .35, 100, 500, 300, 30), RestClient.builder(), Clock.systemUTC());
	}

	static SearchQuery query(List<List<String>> words, List<String> categories) {
		return new SearchQuery(words, List.of(List.of("대학생")), categories, "서울", List.of("11"), 24, false, true, true);
	}

	@Test
	void hybridRequestCachesAndValidatesScoresWithoutSendingProfileFilters() {
		var client = client();
		var query = query(List.of(List.of("등록금", "학비")), List.of("교육"));
		var result = client.search(query);
		assertThat(result.used()).isTrue();
		assertThat(result.hits()).containsExactly(new HarvesterSemanticSearch.Hit("valid", .65));
		assertThat(client.search(query)).isEqualTo(result);
		assertThat(requests).hasSize(1);
		assertThat(requests.getFirst()).contains("q=등록금", "search_mode=hybrid", "status=active", "limit=100")
			.doesNotContain("서울", "24", "대학생", "age=", "region=");
		// Removing the last benefit chip must not retrieve the removed topic again.
		assertThat(client.search(query.without(false, false, false, false, List.of("등록금"))).used()).isFalse();
		assertThat(requests).hasSize(1);
	}

	@Test
	void broadRecommendationAndOtherCategoriesDoNotCallHarvester() {
		var client = client();
		assertThat(client.search(query(List.of(), List.of())).used()).isFalse();
		assertThat(client.search(query(List.of(List.of("면접 정장")), List.of("일자리"))).used()).isFalse();
		assertThat(requests).isEmpty();
	}

	@Test
	void rateLimitResponseDoesNotRetryAndDoesNotBreakSearch() {
		status = 429;
		var client = client();
		var q = query(List.of(List.of("등록금")), List.of("교육"));
		assertThat(client.search(q).used()).isFalse();
		assertThat(client.search(q).used()).isFalse();
		assertThat(requests).hasSize(1);
	}

	@Test
	void lexicalFallbackFromHarvesterIsNotReportedAsVectorSearch() {
		body = "{\"query\":{\"vector_used\":false},\"items\":[{\"opportunity_id\":\"valid\",\"vector_score\":0.9}]}";
		assertThat(client().search(query(List.of(List.of("등록금")), List.of("교육"))).used()).isFalse();
	}

	@Test
	void slowHarvesterFallsBackWithinTheConfiguredReadTimeout() {
		delayMillis = 1500;
		var client = client();
		long start = System.nanoTime();
		assertThat(client.search(query(List.of(List.of("등록금")), List.of("교육"))).used()).isFalse();
		assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1200);
		assertThat(requests).hasSize(1);
	}
}
