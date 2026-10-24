package com.youthbenefit.eligibility;

import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import com.youthbenefit.policy.PolicySource;
import com.youthbenefit.policy.PolicySummaryResponse;
import com.youthbenefit.profile.UserProfileRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
		ConditionEvaluator.Evaluation evaluation = ConditionEvaluator.evaluate(conditions(policy), facts);
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

	/** 공고의 조건을 사람이 읽을 수 있는 줄로 (판정은 facts 기준). 변경 알림에서 조건끼리 비교할 때도 쓴다. */
	public static List<ConditionResult> describe(Policy policy, ProfileFacts facts) {
		return ConditionEvaluator.evaluate(conditions(policy), facts).conditions();
	}

	/** 출처마다 자격 정보 모양이 달라서 조건 나무를 만드는 곳이 다르다. 판정은 같은 엔진으로 한다. */
	static Condition conditions(Policy policy) {
		return policy.getSource() == PolicySource.SCHOOL ? HarvesterConditions.from(policy) : OntongConditions.from(policy);
	}

	@Transactional(readOnly = true)
	/**
	 * @param closingWithinDays 주면 오늘부터 그 날수 안에 마감하는 공고만("이번 주 마감" = 7). 마감일 없는 상시 모집은 빠진다.
	 */
	public MatchesResponse matches(UUID anonymousId, String category, Verdict verdictFilter, boolean includeNoMatch,
			Integer closingWithinDays, int page, int size) {
		ProfileFacts facts = facts(anonymousId);
		LocalDate today = today();
		List<Policy> open = openPolicies(category, today);
		if (closingWithinDays != null) {
			LocalDate last = today.plusDays(closingWithinDays);
			open = open.stream().filter(p -> p.getApplyEndDate() != null && !p.getApplyEndDate().isAfter(last)).toList();
		}

		List<MatchItem> judged = open.stream().map(p -> {
			PolicyEligibility e = judge(p, facts);
			Integer dDay = p.getApplyEndDate() == null ? null
					: (int) ChronoUnit.DAYS.between(today, p.getApplyEndDate());
			return new MatchItem(PolicySummaryResponse.from(p), e.verdict(), e.verdictLabel(), e.summary(), dDay);
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

	private List<Policy> openPolicies(String category, LocalDate today) {
		Pageable all = Pageable.unpaged(Sort.by(Sort.Order.asc("applyEndDate").nullsLast(), Sort.Order.desc("id")));
		return (category == null || category.isBlank()) ? policyRepository.findOpen(today, all).getContent()
				: policyRepository.findOpenByCategory(today, category, all).getContent();
	}

	/** 판정 변수 → 프로필 항목 이름과 화면 이름. 나이 조건은 생년월일로 채운다. */
	private static final Map<String, String[]> FIELDS = Map.ofEntries(Map.entry("age", new String[] { "birthDate", "생년월일" }),
			Map.entry("zipCd", new String[] { "zipCd", "지역" }),
			Map.entry("annualIncome", new String[] { "annualIncome", "소득" }),
			Map.entry("householdMedianIncomePct", new String[] { "householdMedianIncomePct", "가구 소득" }),
			Map.entry("jobCd", new String[] { "jobCd", "취업 상태" }), Map.entry("schoolCd", new String[] { "schoolCd", "학력" }),
			Map.entry("mrgSttsCd", new String[] { "mrgSttsCd", "결혼 여부" }),
			Map.entry("plcyMajorCd", new String[] { "plcyMajorCd", "전공" }),
			Map.entry("sbizCd", new String[] { "sbizCd", "해당 대상" }),
			Map.entry("housingType", new String[] { "housingType", "주거 형태" }),
			Map.entry("homeowner", new String[] { "homeowner", "주택 소유 여부" }),
			Map.entry("schoolName", new String[] { "schoolName", "학교" }),
			Map.entry("enrollmentStatus", new String[] { "enrollmentStatus", "재학 상태" }),
			Map.entry("gradeLevel", new String[] { "gradeLevel", "학년" }), Map.entry("gpa", new String[] { "gpa", "학점" }),
			Map.entry("kosafIncomeBracket", new String[] { "kosafIncomeBracket", "학자금 지원구간" }));

	/**
	 * 홈 배너(WF02): 비어 있는 내 정보마다, 채우면 판정이 확정되는 공고 수를 세서 효과 큰 순서로 준다.
	 * "확정"은 그 항목 하나만 모르는 채로 확인 필요인 공고다 — 글로 된 조건(원문 확인)이 남은 공고는 채워도 확인 필요라 세지 않는다.
	 */
	@Transactional(readOnly = true)
	public List<ProfileSuggestion> suggestions(UUID anonymousId) {
		ProfileFacts facts = facts(anonymousId);
		Map<String, Integer> resolvable = new HashMap<>();
		Map<String, Integer> affected = new HashMap<>();
		for (Policy policy : openPolicies(null, today())) {
			ConditionEvaluator.Evaluation evaluation = ConditionEvaluator.evaluate(conditions(policy), facts);
			if (Verdict.of(evaluation.truth()) != Verdict.NEEDS_CHECK) {
				continue;
			}
			List<ConditionResult> unknown = evaluation.conditions().stream().filter(c -> c.truth() == Truth.UNKNOWN).toList();
			Set<String> missing = unknown.stream()
				.map(ConditionResult::missingField)
				.filter(Objects::nonNull)
				.map(var -> FIELDS.containsKey(var) ? FIELDS.get(var)[0] : var)
				.collect(Collectors.toCollection(LinkedHashSet::new));
			missing.forEach(f -> affected.merge(f, 1, Integer::sum));
			if (missing.size() == 1 && unknown.stream().allMatch(c -> c.missingField() != null)) {
				resolvable.merge(missing.iterator().next(), 1, Integer::sum);
			}
		}
		Map<String, String> labels = new HashMap<>();
		FIELDS.values().forEach(v -> labels.put(v[0], v[1]));
		return affected.keySet()
			.stream()
			.map(field -> {
				String label = labels.getOrDefault(field, field);
				int r = resolvable.getOrDefault(field, 0);
				int a = affected.get(field);
				String message = r > 0 ? label + objectParticle(label) + " 입력하면 공고 " + r + "개가 더 판정돼요"
						: label + objectParticle(label) + " 입력하면 공고 " + a + "개를 더 정확히 볼 수 있어요";
				return new ProfileSuggestion(field, label, r, a, message);
			})
			.sorted(Comparator.comparingInt(ProfileSuggestion::resolvable)
				.thenComparingInt(ProfileSuggestion::affected)
				.reversed()
				.thenComparing(ProfileSuggestion::field))
			.toList();
	}

	/** 받침이 있으면 "을", 없으면 "를" ("소득을", "학교를") */
	static String objectParticle(String word) {
		char last = word.charAt(word.length() - 1);
		if (last < 0xAC00 || last > 0xD7A3) {
			return "을(를)";
		}
		return (last - 0xAC00) % 28 == 0 ? "를" : "을";
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
