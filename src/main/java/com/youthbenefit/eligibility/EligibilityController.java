package com.youthbenefit.eligibility;

import com.youthbenefit.auth.CurrentOwner;
import com.youthbenefit.policy.PolicyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
					+ "verdict 로 한 가지만, includeNoMatch=true 면 불일치까지 본다. closingWithinDays=7 이면 7일 안에 마감하는 공고만(이번 주 마감). "
					+ "counts 는 verdict·includeNoMatch 와 상관없는 건수(category·closingWithinDays 는 반영). 카드마다 dDay(상시 모집은 null)")
	@GetMapping("/api/v1/me/matches")
	public MatchesResponse matches(
			@CurrentOwner(required = false) UUID anonymousId,
			@RequestParam(required = false) String category, @RequestParam(required = false) Verdict verdict,
			@RequestParam(defaultValue = "false") boolean includeNoMatch,
			@RequestParam(required = false) Integer closingWithinDays, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return eligibilityService.matches(anonymousId, category, verdict, includeNoMatch,
				closingWithinDays == null ? null : Math.clamp(closingWithinDays, 0, 365), Math.max(page, 0),
				Math.clamp(size, 1, 100));
	}

	public record Suggestions(List<ProfileSuggestion> items) {
	}

	@Operation(summary = "입력 추천 (홈 배너)",
			description = "비어 있는 내 정보마다 채우면 판정이 확정되는 공고 수. 효과 큰 순서. 첫 번째를 배너에 쓰면 된다. 다 채웠으면 빈 목록")
	@GetMapping("/api/v1/me/suggestions")
	public Suggestions suggestions(
			@CurrentOwner(required = false) UUID anonymousId) {
		return new Suggestions(eligibilityService.suggestions(anonymousId));
	}

	@Operation(summary = "공고 하나 판정", description = "조건마다 공고 요구사항, 내 값, 참/거짓/미확인, 근거를 준다")
	@GetMapping("/api/v1/policies/{id}/eligibility")
	public PolicyEligibility eligibility(@PathVariable Long id,
			@CurrentOwner(required = false) UUID anonymousId) {
		return policyRepository.findById(id)
			.filter(p -> p.visibleTo(anonymousId))
			.map(p -> eligibilityService.judge(p, eligibilityService.facts(anonymousId)))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다: " + id));
	}

}
