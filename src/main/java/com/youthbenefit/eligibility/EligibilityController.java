package com.youthbenefit.eligibility;

import com.youthbenefit.policy.PolicyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "맞춤 판정", description = "WF02 맞춤 홈, WF03 내 조건과 비교")
@RestController
@RequiredArgsConstructor
public class EligibilityController {

	private final EligibilityService eligibilityService;

	private final PolicyRepository policyRepository;

	@Operation(summary = "맞춤 공고 목록",
			description = "지금 신청 가능한 공고를 내 프로필로 판정한다. 기본은 조건 일치 + 확인 필요(불일치 제외), 마감 임박순. "
					+ "verdict 로 한 가지만, includeNoMatch=true 면 불일치까지 본다. counts 는 필터와 상관없는 전체 건수")
	@GetMapping("/api/v1/me/matches")
	public MatchesResponse matches(
			@Parameter(description = "브라우저 UUID. 없으면 빈 프로필로 판정") @RequestHeader(value = "X-Anonymous-Id",
					required = false) UUID anonymousId,
			@RequestParam(required = false) String category, @RequestParam(required = false) Verdict verdict,
			@RequestParam(defaultValue = "false") boolean includeNoMatch, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return eligibilityService.matches(anonymousId, category, verdict, includeNoMatch, Math.max(page, 0),
				Math.clamp(size, 1, 100));
	}

	@Operation(summary = "공고 하나 판정", description = "조건마다 공고 요구사항, 내 값, 참/거짓/미확인, 근거를 준다")
	@GetMapping("/api/v1/policies/{id}/eligibility")
	public PolicyEligibility eligibility(@PathVariable Long id,
			@Parameter(description = "브라우저 UUID. 없으면 빈 프로필로 판정") @RequestHeader(value = "X-Anonymous-Id",
					required = false) UUID anonymousId) {
		return policyRepository.findById(id)
			.map(p -> eligibilityService.judge(p, eligibilityService.facts(anonymousId)))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다: " + id));
	}

}
