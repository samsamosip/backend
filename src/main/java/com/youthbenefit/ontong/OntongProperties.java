package com.youthbenefit.ontong;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 ontong.* 설정. API 키는 .env 의 ONTONG_API_KEY 에서 읽는다. */
@ConfigurationProperties("ontong")
public record OntongProperties(String baseUrl, String apiKey, int pageSize, Sync sync) {

	public record Sync(boolean scheduled, String cron) {
	}

}
