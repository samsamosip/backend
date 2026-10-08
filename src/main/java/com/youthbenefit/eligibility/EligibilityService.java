package com.youthbenefit.eligibility;

import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import com.youthbenefit.policy.PolicySummaryResponse;
import com.youthbenefit.profile.UserProfileRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로필과 공고 조건을 비교해 조건 일치 / 확인 필요 / 조건 불일치를 판정한다. LLM 은 쓰지 않는다. */
@Service
@RequiredArgsConstructor
public class EligibilityService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final PolicyRepository policyRepository;

	private final UserProfileRepository profileRepository;

	/** 프로필이 없으면(헤더 없음 포함) 빈 프로필로 판정한다 — 조건이 있는 공고는 모두 "확인 필요"가 된다. */
	@Transactional(readOnly = true)
	public ProfileFacts facts(UUID anonymousId) {
		if (anonymousId == null) {
			return ProfileFacts.empty();
		}
		return profileRepository.findByAnonymousId(anonymousId)
			.map(p -> ProfileFacts.of(p, today()))
			.orElse(ProfileFacts.empty());
	}

	public PolicyEligibility judge(Policy policy, ProfileFacts facts) {
		ConditionEvaluator.Evaluation evaluation = ConditionEvaluator.evaluate(OntongConditions.from(policy), facts);
		Verdict verdict = Verdict.of(evaluation.truth());
		List<String> missing = evaluation.conditions()
			.stream()
			.map(ConditionResult::missingField)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		return new PolicyEligibility(policy.getId(), verdict, verdict.label(), summary(verdict, evaluation),
				evaluation.conditions(), missing);
	}

	@Transactional(readOnly = true)
	public MatchesResponse matches(UUID anonymousId, String category, Verdict verdictFilter, boolean includeNoMatch,
			int page, int size) {
		ProfileFacts facts = facts(anonymousId);
		LocalDate today = today();
		Pageable all = Pageable.unpaged(Sort.by(Sort.Order.asc("applyEndDate").nullsLast(), Sort.Order.desc("id")));
		List<Policy> open = (category == null || category.isBlank()) ? policyRepository.findOpen(today, all).getContent()
				: policyRepository.findOpenByCategory(today, category, all).getContent();

		List<MatchItem> judged = open.stream().map(p -> {
			PolicyEligibility e = judge(p, facts);
			return new MatchItem(PolicySummaryResponse.from(p), e.verdict(), e.verdictLabel(), e.summary());
		}).toList();

		MatchesResponse.Counts counts = new MatchesResponse.Counts(count(judged, Verdict.MATCH),
				count(judged, Verdict.NEEDS_CHECK), count(judged, Verdict.NO_MATCH));
		List<MatchItem> filtered = judged.stream()
			.filter(m -> verdictFilter != null ? m.verdict() == verdictFilter
					: includeNoMatch || m.verdict() != Verdict.NO_MATCH)
			.toList();

		int from = Math.min(page * size, filtered.size());
		int to = Math.min(from + size, filtered.size());
		int totalPages = size == 0 ? 0 : (filtered.size() + size - 1) / size;
		return new MatchesResponse(counts, filtered.subList(from, to), page, size, filtered.size(), totalPages);
	}

	private static long count(List<MatchItem> items, Verdict verdict) {
		return items.stream().filter(m -> m.verdict() == verdict).count();
	}

	static String summary(Verdict verdict, ConditionEvaluator.Evaluation evaluation) {
		List<ConditionResult> conditions = evaluation.conditions();
		return switch (verdict) {
			case MATCH -> conditions.isEmpty() ? "자격 제한이 없는 공고예요" : "입력한 조건이 모두 맞아요";
			case NO_MATCH -> labels(conditions, Truth.FALSE) + " 조건이 맞지 않아요";
			case NEEDS_CHECK -> labels(conditions, Truth.UNKNOWN) + " 확인 필요";
		};
	}

	private static String labels(List<ConditionResult> conditions, Truth truth) {
		Set<String> labels = conditions.stream()
			.filter(c -> c.truth() == truth)
			.map(ConditionResult::label)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return String.join("·", labels);
	}

	private static LocalDate today() {
		return LocalDate.now(KST);
	}

}
