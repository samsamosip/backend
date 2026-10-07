package com.youthbenefit.ontong;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 온통청년 청년정책 API 호출. GET /go/ythip/getPlcy?apiKeyNm=&pageNum=&pageSize=&rtnType=json
 */
@Component
public class OntongApiClient {

	private static final String PATH = "/go/ythip/getPlcy";

	/** 온통청년 서버가 가끔 첫 요청을 끊는다. 실패하면 이 간격으로 다시 시도한다. */
	private static final List<Duration> DEFAULT_RETRY_DELAYS = List.of(Duration.ofSeconds(2), Duration.ofSeconds(5));

	private final OntongProperties properties;

	private final RestClient restClient;

	private final JsonMapper jsonMapper;

	private final List<Duration> retryDelays;

	@Autowired
	public OntongApiClient(OntongProperties properties, RestClient.Builder restClientBuilder, JsonMapper jsonMapper) {
		this(properties, restClientBuilder.requestFactory(requestFactory()), jsonMapper, DEFAULT_RETRY_DELAYS);
	}

	OntongApiClient(OntongProperties properties, RestClient.Builder restClientBuilder, JsonMapper jsonMapper,
			List<Duration> retryDelays) {
		this.properties = properties;
		this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
		this.jsonMapper = jsonMapper;
		this.retryDelays = retryDelays;
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(60));
		return factory;
	}

	/** 모든 페이지를 돌며 정책 전체를 받는다. */
	public List<Map<String, Object>> fetchAll() {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new OntongApiException("ONTONG_API_KEY가 설정되지 않았습니다. .env 파일을 확인하세요.");
		}
		List<Map<String, Object>> all = new ArrayList<>();
		int page = 1;
		while (true) {
			OntongResponse.Result result = fetchPage(page);
			List<Map<String, Object>> items = result.youthPolicyList() == null ? List.of() : result.youthPolicyList();
			all.addAll(items);
			int total = result.pagging() == null ? 0 : result.pagging().totCount();
			if (items.isEmpty() || all.size() >= total) {
				return all;
			}
			page++;
		}
	}

	OntongResponse.Result fetchPage(int pageNum) {
		String body = requestWithRetry(pageNum);
		// 키가 틀리면 rtnType=json 이어도 XML(<errorMsg>invalid api key.</errorMsg>)이 온다
		if (body == null || !body.stripLeading().startsWith("{")) {
			throw new OntongApiException("온통청년 API가 JSON이 아닌 응답을 보냈습니다: " + abbreviate(body));
		}
		OntongResponse response;
		try {
			response = jsonMapper.readValue(body, OntongResponse.class);
		}
		catch (JacksonException ex) {
			throw new OntongApiException("온통청년 API 응답을 해석하지 못했습니다: " + abbreviate(body), ex);
		}
		if (response.resultCode() != 200 || response.result() == null) {
			throw new OntongApiException(
					"온통청년 API 오류 " + response.resultCode() + ": " + response.resultMessage());
		}
		return response.result();
	}

	private String requestWithRetry(int pageNum) {
		RestClientException last = null;
		for (int attempt = 0; attempt <= retryDelays.size(); attempt++) {
			if (attempt > 0) {
				sleep(retryDelays.get(attempt - 1));
			}
			try {
				return restClient.get()
					.uri(uri -> uri.path(PATH)
						.queryParam("apiKeyNm", properties.apiKey())
						.queryParam("pageNum", pageNum)
						.queryParam("pageSize", properties.pageSize())
						.queryParam("rtnType", "json")
						.build())
					.retrieve()
					.body(String.class);
			}
			catch (RestClientException ex) {
				last = ex;
			}
		}
		throw new OntongApiException("온통청년 API 호출 실패 (page " + pageNum + ", " + (retryDelays.size() + 1)
				+ "번 시도): " + last.getMessage(), last);
	}

	private static void sleep(Duration delay) {
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new OntongApiException("온통청년 API 재시도 중 중단됨", ex);
		}
	}

	private static String abbreviate(String body) {
		if (body == null) {
			return "(빈 응답)";
		}
		return body.length() <= 200 ? body : body.substring(0, 200) + "…";
	}

}
