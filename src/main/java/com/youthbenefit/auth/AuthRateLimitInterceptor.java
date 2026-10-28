package com.youthbenefit.auth;

import com.youthbenefit.common.ErrorResponse;
import com.youthbenefit.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 가입·로그인 남용 막기 (사람 = IP 기준, 서버 메모리에서 센다).
 * <ul>
 * <li>메일을 보내는 요청(가입, 코드 다시 받기, 비밀번호 찾기): 1시간에 20번 — 남의 주소로 메일 폭탄을 보내 Gmail 계정이 막히지 않게</li>
 * <li>맞히는 요청(로그인, 코드 확인, 비밀번호 재설정): 10분에 60번 — 여러 계정을 돌아가며 비밀번호를 찍는 것 막기</li>
 * </ul>
 * 행사장처럼 여러 사람이 한 와이파이(IP)를 써도 막히지 않게 넉넉하게 잡았다. 한 주소당 하루 10통, 서버 전체 하루 메일 수는
 * {@link AuthService}·{@link AuthMailer} 에서 따로 막는다.
 */
class AuthRateLimitInterceptor implements HandlerInterceptor {

	private static final Set<String> SENDS_MAIL = Set.of("/api/v1/auth/signup", "/api/v1/auth/email/resend",
			"/api/v1/auth/password/forgot");

	private static final Set<String> GUESSES = Set.of("/api/v1/auth/login", "/api/v1/auth/email/verify",
			"/api/v1/auth/password/verify-code", "/api/v1/auth/password/reset");

	private final RateLimiter mail;

	private final RateLimiter attempts;

	private final JsonMapper jsonMapper;

	AuthRateLimitInterceptor(int mailPerHour, int attemptsPer10Minutes, Clock clock, JsonMapper jsonMapper) {
		this.mail = new RateLimiter(mailPerHour, Duration.ofHours(1), clock);
		this.attempts = new RateLimiter(attemptsPer10Minutes, Duration.ofMinutes(10), clock);
		this.jsonMapper = jsonMapper;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (!"POST".equals(request.getMethod())) {
			return true;
		}
		String path = request.getRequestURI();
		String ip = request.getRemoteAddr();
		if (SENDS_MAIL.contains(path) && !mail.tryAcquire(ip)) {
			return reject(response, "MAIL_RATE_LIMITED", "인증 메일 요청이 너무 많아요. 1시간 뒤에 다시 해 주세요.");
		}
		if (GUESSES.contains(path) && !attempts.tryAcquire(ip)) {
			return reject(response, "TOO_MANY_ATTEMPTS", "시도가 너무 많아요. 10분 뒤에 다시 해 주세요.");
		}
		return true;
	}

	private boolean reject(HttpServletResponse response, String code, String message) throws Exception {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(jsonMapper.writeValueAsString(new ErrorResponse(message, Map.of(), code)));
		return false;
	}

}
