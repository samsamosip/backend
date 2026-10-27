package com.youthbenefit.search;

import com.youthbenefit.auth.CurrentOwner;
import com.youthbenefit.common.ApiException;
import com.youthbenefit.common.InvalidRequestException;
import com.youthbenefit.common.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "자연어 검색", description = "WF06")
@RestController
public class SearchController {

	private final SearchService service;

	/** 한 사람(IP + 기기)이 1분에 할 수 있는 검색 수. 넘으면 429 */
	private final RateLimiter perClient;

	/** 한 IP 전체(같은 와이파이의 여러 사람)의 1분 검색 수. 자동 요청 막기용이라 넉넉하게 */
	private final RateLimiter perIp;

	/** 한 사람이 1시간에 AI 해석을 쓸 수 있는 수. 넘으면 단어 검색으로 (오류 아님) */
	private final RateLimiter aiPerClient;

	public SearchController(SearchService service, Clock clock,
			@Value("${search.rate-limit.per-minute:30}") int perMinute,
			@Value("${search.rate-limit.per-ip-per-minute:300}") int perIpPerMinute,
			@Value("${search.rate-limit.ai-per-hour:30}") int aiPerHour) {
		this.service = service;
		this.perClient = new RateLimiter(perMinute, Duration.ofMinutes(1), clock);
		this.perIp = new RateLimiter(perIpPerMinute, Duration.ofMinutes(1), clock);
		this.aiPerClient = new RateLimiter(aiPerHour, Duration.ofHours(1), clock);
	}

	@Operation(summary = "자연어 검색",
			description = "\"서울 사는 대학생 월세 지원\" 같은 문장으로 지금 신청 가능한 공고를 찾고 내 프로필로 판정한다. understood 는 알아들은 조건(화면 칩: "
					+ "keywords 단어 묶음, categories, region, age, closingSoon, ai). 기본은 불일치 제외(includeNoMatch=true 면 포함). "
					+ "결과가 없으면 message 에 안내(WF06-b). 로그인 토큰이나 X-Anonymous-Id 가 없으면 빈 프로필로 판정. "
					+ "1분에 30번 넘게 검색하면 429 RATE_LIMITED, 1시간에 AI 해석 30번이 넘으면 단어로 찾고 notice 로 알린다")
	@GetMapping("/api/v1/search")
	public SearchService.SearchResponse search(@RequestParam String q, @CurrentOwner(required = false) UUID owner,
			HttpServletRequest request,
			@RequestParam(defaultValue = "false") boolean includeNoMatch, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		if (q.isBlank() || q.length() > 200) {
			throw new InvalidRequestException(Map.of("q", "검색어는 1~200자로 써 주세요"));
		}
		String ip = request.getRemoteAddr();
		String client = ip + "|" + (owner != null ? owner : "-");
		if (!perIp.tryAcquire(ip) || !perClient.tryAcquire(client)) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "검색이 너무 잦아요. 잠시 후 다시 해 주세요.");
		}
		return service.search(owner, q, includeNoMatch, Math.max(page, 0), Math.clamp(size, 1, 100),
				aiPerClient.tryAcquire(client));
	}

}
