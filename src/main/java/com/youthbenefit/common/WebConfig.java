package com.youthbenefit.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	/** 프론트 개발 서버(다른 포트)에서 API 를 부를 수 있게 허용한다. 허용 주소는 CORS_ALLOWED_ORIGINS. */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
			.allowedOrigins(allowedOrigins)
			.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("*")
			.maxAge(3600);
	}

	@Bean
	OpenAPI openApi() {
		return new OpenAPI().info(new Info().title("청년혜택 길잡이 API")
			.version("v1")
			.description("""
					온통청년 정책 조회와 사용자 프로필 API.
					/api/v1/me 경로는 로그인 전까지 X-Anonymous-Id 헤더(UUID)로 사용자를 구분한다.
					오류는 {"message": "...", "errors": {"항목": "이유"}} 형식이다."""));
	}

}
