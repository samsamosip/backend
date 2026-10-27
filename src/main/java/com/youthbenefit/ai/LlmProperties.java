package com.youthbenefit.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PwC LLM 게이트웨이(OpenAI 방식, LiteLLM). 모델 이름은 "openai.gpt-4.1-mini" 처럼 앞에 공급자가 붙는다.
 *
 * @param model 기본(싼) 모델 — 1차 변환
 * @param reviewModel 애매한 것만 다시 보는 모델 — 2차 확인
 */
@ConfigurationProperties("llm")
public record LlmProperties(String baseUrl, String apiKey, String model, String reviewModel, int timeoutSeconds) {

	public boolean configured() {
		return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
	}

}
