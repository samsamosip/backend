package com.youthbenefit.common;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 무료 서버(Render 무료는 15분 동안 손님이 없으면 잠들고, 깨는 데 1~2분)가 잠들지 않게 10분마다 자기 바깥 주소를 부른다.
 * KEEP_AWAKE_URL(예: https://youthbenefit-api.onrender.com)이 있을 때만 켜진다. 정식 서버에는 넣지 않는다.
 */
@Slf4j
@Component
@ConditionalOnExpression("!'${app.keep-awake-url:}'.isBlank()")
public class KeepAwakeScheduler {

	private final RestClient restClient;

	private final String url;

	public KeepAwakeScheduler(@Value("${app.keep-awake-url}") String baseUrl, RestClient.Builder builder) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(30));
		this.restClient = builder.clone().requestFactory(factory).build();
		this.url = baseUrl.strip().replaceAll("/+$", "") + "/actuator/health";
		log.info("잠들지 않게 10분마다 {} 를 불러요", url);
	}

	@Scheduled(initialDelay = 60_000, fixedDelay = 600_000)
	public void ping() {
		try {
			restClient.get().uri(url).retrieve().toBodilessEntity();
		}
		catch (RestClientException ex) {
			log.warn("잠 깨우기 요청 실패: {}", ex.getMessage());
		}
	}

}
