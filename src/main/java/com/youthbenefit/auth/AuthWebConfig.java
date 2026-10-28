package com.youthbenefit.auth;

import java.util.List;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class AuthWebConfig implements WebMvcConfigurer {

	static {
		// API 문서에 이 파라미터를 칸으로 보여주지 않는다 (대신 문서 상단 인증 설명)
		SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentOwner.class, LoginUser.class);
	}

	private final TokenService tokenService;

	private final UserRepository users;

	private final AuthRateLimitInterceptor rateLimit;

	AuthWebConfig(TokenService tokenService, UserRepository users, java.time.Clock clock,
			tools.jackson.databind.json.JsonMapper jsonMapper,
			@org.springframework.beans.factory.annotation.Value("${app.auth.rate-limit.mail-per-hour:20}") int mailPerHour,
			@org.springframework.beans.factory.annotation.Value("${app.auth.rate-limit.attempts-per-10-minutes:60}") int attempts) {
		this.tokenService = tokenService;
		this.users = users;
		this.rateLimit = new AuthRateLimitInterceptor(mailPerHour, attempts, clock, jsonMapper);
	}

	@Override
	public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
		registry.addInterceptor(rateLimit).addPathPatterns("/api/v1/auth/**");
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new OwnerArgumentResolver(tokenService, users));
	}

}
