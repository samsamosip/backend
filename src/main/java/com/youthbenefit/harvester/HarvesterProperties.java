package com.youthbenefit.harvester;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 harvester.* 설정. 주소와 키는 .env 의 HARVESTER_BASE_URL, HARVESTER_API_KEY 에서 읽는다. */
@ConfigurationProperties("harvester")
public record HarvesterProperties(String baseUrl, String apiKey, Sync sync) {

	public record Sync(boolean scheduled, String cron) {
	}

}
