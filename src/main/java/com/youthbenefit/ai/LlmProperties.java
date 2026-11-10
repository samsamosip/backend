package com.youthbenefit.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PwC LLM 게이트웨이(OpenAI 방식, LiteLLM). 모델 이름과 요청 옵션은 PWC_LLM_* 환경변수로 설정한다.
 *
 * @param model 기본(싼) 모델 — 1차 변환
 * @param reviewModel 애매한 것만 다시 보는 모델 — 2차 확인
 * @param temperature 모델별 샘플링 값. GPT-6 계열은 게이트웨이에서 1만 허용한다
 * @param reasoningEffort 추론량. none은 짧은 검색·추출용, 비우면 모델 기본값
 * @param dailyTokenLimit 하루(한국 시간) 토큰 한도. 넘으면 다음 날까지 AI 를 부르지 않는다(검색은 단어로, 변환은 다음 수집 때). 0 이면 제한 없음
 */
@ConfigurationProperties("llm")
public record LlmProperties(String baseUrl, String apiKey, String model, String reviewModel, int timeoutSeconds,
		long dailyTokenLimit, double temperature, String reasoningEffort) {

	public boolean configured() {
		return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
	}

}
