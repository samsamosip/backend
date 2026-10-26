package com.youthbenefit.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	private final String adminApiKey;

	private final JsonMapper jsonMapper;

	public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins,
			@Value("${app.admin.api-key:}") String adminApiKey, JsonMapper jsonMapper) {
		this.allowedOrigins = allowedOrigins;
		this.adminApiKey = adminApiKey;
		this.jsonMapper = jsonMapper;
	}

	/** 관리자 API 잠금. ADMIN_API_KEY 가 있을 때만 동작한다({@link AdminKeyInterceptor}). */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new AdminKeyInterceptor(adminApiKey, jsonMapper)).addPathPatterns("/api/v1/admin/**");
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
					/api/v1/me 경로는 로그인했으면 Authorization: Bearer {accessToken}, 아니면 X-Anonymous-Id 헤더(UUID)로 사용자를 구분한다.
					오른쪽 위 Authorize 에 둘 중 하나를 넣고 시험하면 된다. /api/v1/me/account 는 로그인 필요.
					오류는 {"message": "...", "errors": {"항목": "이유"}, "code": "로그인 오류 구분"} 형식이다.
					관리자 API(/api/v1/admin)는 서버에 ADMIN_API_KEY 가 설정돼 있으면 X-Admin-Key 헤더가 필요하다."""))
			.components(new Components()
				.addSecuritySchemes("login", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
				.addSecuritySchemes("anonymous",
						new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Anonymous-Id")))
			.addSecurityItem(new SecurityRequirement().addList("login"))
			.addSecurityItem(new SecurityRequirement().addList("anonymous"));
	}

}
