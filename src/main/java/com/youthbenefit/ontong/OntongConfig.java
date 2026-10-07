package com.youthbenefit.ontong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class OntongConfig {

	/** Spring Boot 4 는 restclient 스타터가 없으면 RestClient.Builder 를 만들어 주지 않는다. */
	@Bean
	@ConditionalOnMissingBean
	RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}

}
