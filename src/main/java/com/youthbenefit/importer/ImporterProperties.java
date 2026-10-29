package com.youthbenefit.importer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 importer.* 설정 (링크 불러오기 서버, AI 팀원). 값은 .env 에만.
 *
 * @param baseUrl 불러오기 서버 주소
 * @param apiKey 불러오기 서버가 백엔드용으로 발급한 X-API-Key
 * @param callbackSecret 결과 콜백 서명(HMAC-SHA256) 공유 비밀키
 * @param callbackBaseUrl 바깥에서 부를 수 있는 이 백엔드 주소. 비어 있으면(로컬) 콜백 없이 직접 조회만 한다
 * @param perHour 한 사람이 1시간에 불러올 수 있는 수
 */
@ConfigurationProperties("importer")
public record ImporterProperties(String baseUrl, String apiKey, String callbackSecret, String callbackBaseUrl,
		int perHour) {

	public boolean configured() {
		return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
	}

	/** 콜백을 받을 수 있는지 (바깥 주소와 서명 키가 둘 다 있을 때만) */
	public boolean callbackEnabled() {
		return callbackBaseUrl != null && !callbackBaseUrl.isBlank() && callbackSecret != null
				&& !callbackSecret.isBlank();
	}

}
