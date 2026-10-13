package com.youthbenefit.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 API(/api/v1/admin/**)는 X-Admin-Key 헤더가 ADMIN_API_KEY 와 같을 때만 연다.
 * ADMIN_API_KEY 가 비어 있으면(로컬 개발) 검사하지 않는다 — 배포 서버에만 넣는다.
 */
public class AdminKeyInterceptor implements HandlerInterceptor {

	public static final String HEADER = "X-Admin-Key";

	private final byte[] key;

	private final JsonMapper jsonMapper;

	public AdminKeyInterceptor(String adminApiKey, JsonMapper jsonMapper) {
		this.key = adminApiKey == null || adminApiKey.isBlank() ? null : adminApiKey.getBytes(StandardCharsets.UTF_8);
		this.jsonMapper = jsonMapper;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (key == null || "OPTIONS".equals(request.getMethod())) {
			return true;
		}
		String sent = request.getHeader(HEADER);
		// 길이와 상관없이 같은 시간에 비교한다
		if (sent != null && MessageDigest.isEqual(key, sent.getBytes(StandardCharsets.UTF_8))) {
			return true;
		}
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter()
			.write(jsonMapper.writeValueAsString(new ErrorResponse("관리자 키(X-Admin-Key)가 필요합니다.", Map.of())));
		return false;
	}

}
