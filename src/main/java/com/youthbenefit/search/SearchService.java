package com.youthbenefit.search;

import com.youthbenefit.eligibility.EligibilityService;
import com.youthbenefit.eligibility.MatchItem;
import com.youthbenefit.eligibility.PolicyCatalog;
import com.youthbenefit.eligibility.MatchesResponse;
import com.youthbenefit.eligibility.PolicyEligibility;
import com.youthbenefit.eligibility.ProfileFacts;
import com.youthbenefit.eligibility.Verdict;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import com.youthbenefit.policy.PolicySummaryResponse;
import com.youthbenefit.policy.Regions;
import com.youthbenefit.profile.UserProfile;
import com.youthbenefit.profile.UserProfileRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자연어 검색 (WF06). 문장을 {@link QueryInterpreter} 로 조건으로 바꾸고, 지금 신청 가능한 공고에서 찾아 내 프로필로 판정해 준다.
 *
 * <p>
 * 찾기: 분류·지역·나이·마감 임박은 거르고, 단어 묶음은 제목(3점)·키워드·분류(2점)·설명·지원 내용·자격 글(1점)에 나오면 점수.
 * "찾는 것" 묶음은 하나는 나와야 하고(찾는 것 없이 "누구"만 있으면 "누구" 묶음이), "누구" 묶음은 점수만 더한다. 지역을 말했으면 그 지역 공고(전국 공고 말고)에 2점.
 * 정렬은 찾는 것 묶음이 몇 개 나왔는지 → 점수 → 판정(일치, 확인 필요, 불일치) → 마감 임박.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final QueryInterpreter interpreter;

	private final PolicyRepository policyRepository;

	private final EligibilityService eligibilityService;

	private final PolicyCatalog catalog;

	private final UserProfileRepository profileRepository;

	/** 화면에서 ×로 뺀 칩. dropWords 는 뺄 단어 묶음의 대표어 */
	public record Removed(boolean region, boolean category, boolean age, boolean closingSoon, List<String> dropWords) {

		public static final Removed NONE = new Removed(false, false, false, false, List.of());

	}

	/**
	 * @param understood 알아들은 조건 (화면 칩)
	 * @param message 결과가 없을 때 안내 (WF06-b), 있으면 null
	 * @param notice AI 해석을 못 써서 단어로 찾았을 때 안내, 아니면 null
	 */
	public record SearchResponse(String query, SearchQuery understood, MatchesResponse.Counts counts,
			List<MatchItem> content, int page, int size, long totalElements, int totalPages, String message,
			String notice) {
	}

	@Transactional(readOnly = true)
	public SearchResponse search(UUID owner, String text, boolean includeNoMatch, int page, int size) {
		return search(owner, text, includeNoMatch, page, size, true);
	}

	/** @param allowAi false 면 AI 없이(사용량 제한) */
	@Transactional(readOnly = true)
	public SearchResponse search(UUID owner, String text, boolean includeNoMatch, int page, int size, boolean allowAi) {
		return search(owner, text, Removed.NONE, includeNoMatch, page, size, allowAi);
	}

	/**
	 * @param removed 화면에서 ×로 뺀 칩. 지역을 빼지 않았고 문장에 지역이 없으면 내 프로필 지역(시도)으로 찾는다
	 */
	@Transactional(readOnly = true)
	public SearchResponse search(UUID owner, String text, Removed removed, boolean includeNoMatch, int page, int size,
			boolean allowAi) {
		SearchQuery query = interpreter.interpret(text, allowAi);
		if (query.regionCodes().isEmpty() && !removed.region() && owner != null) {
			String zip = profileRepository.findByAnonymousId(owner).map(UserProfile::getZipCd).orElse(null);
			if (zip != null && zip.length() >= 2 && Regions.sidoName(zip.substring(0, 2)) != null) {
				query = query.withProfileRegion(Regions.sidoName(zip.substring(0, 2)), List.of(zip.substring(0, 2)));
			}
		}
		query = query.without(removed.region(), removed.category(), removed.age(), removed.closingSoon(),
				removed.dropWords());
		LocalDate today = LocalDate.now(KST);
		List<PolicyCatalog.Entry> entries = catalog.open(today);
		List<Policy> open = entries.stream().map(PolicyCatalog.Entry::policy).toList();
		java.util.Map<Long, PolicyCatalog.Entry> byId = entries.stream()
			.collect(java.util.stream.Collectors.toMap(e -> e.policy().getId(), e -> e));
		ProfileFacts facts = eligibilityService.facts(owner);

		List<Scored> found = find(open, query, today);
		List<Ranked> ranked = found.stream().map(s -> {
			PolicyCatalog.Entry entry = byId.get(s.policy().getId());
			PolicyEligibility e = eligibilityService.judge(s.policy(), entry.condition(), facts);
			Integer dDay = s.policy().getApplyEndDate() == null ? null
					: (int) ChronoUnit.DAYS.between(today, s.policy().getApplyEndDate());
			return new Ranked(s.needHits(), s.score(), new MatchItem(entry.summary(), e.verdict(), e.verdictLabel(),
					e.summary(), dDay));
		})
			.sorted(Comparator.comparingInt(Ranked::needHits)
				.thenComparingInt(Ranked::score)
				.reversed()
				.thenComparingInt(r -> order(r.item().verdict()))
				.thenComparing(r -> r.item().dDay() == null ? Integer.MAX_VALUE : r.item().dDay()))
			.toList();

		MatchesResponse.Counts counts = new MatchesResponse.Counts(count(ranked, Verdict.MATCH),
				count(ranked, Verdict.NEEDS_CHECK), count(ranked, Verdict.NO_MATCH));
		List<MatchItem> shown = ranked.stream()
			.map(Ranked::item)
			.filter(m -> includeNoMatch || m.verdict() != Verdict.NO_MATCH)
			.toList();
		int from = Math.min(page * size, shown.size());
		int to = Math.min(from + size, shown.size());
		return new SearchResponse(text, query, counts, shown.subList(from, to), page, size, shown.size(),
				(shown.size() + size - 1) / size, shown.isEmpty() ? emptyMessage(open, query, today, ranked.size()) : null,
				query.ai() ? null : "지금은 AI 검색을 쉬고 있어서 단어로 찾았어요");
	}

	/** 결과가 없을 때: 지역을 빼면 있는지, 불일치만 있는지 알려 준다 */
	private String emptyMessage(List<Policy> open, SearchQuery query, LocalDate today, int allVerdicts) {
		if (allVerdicts > 0) {
			return "찾은 공고 " + allVerdicts + "개가 모두 내 조건과 맞지 않아요. 불일치도 보려면 includeNoMatch=true";
		}
		if (!query.regionCodes().isEmpty()) {
			int withoutRegion = find(open, query.withoutRegion(), today).size();
			if (withoutRegion > 0) {
				return query.region() + "에서는 못 찾았어요. 지역을 빼면 " + withoutRegion + "개 있어요";
			}
		}
		return "조건에 맞는 공고를 못 찾았어요. 다른 말로 검색해 보세요";
	}

	record Scored(Policy policy, int needHits, int score) {
	}

	private record Ranked(int needHits, int score, MatchItem item) {
	}

	static List<Scored> find(List<Policy> open, SearchQuery q, LocalDate today) {
		List<Scored> out = new ArrayList<>();
		for (Policy p : open) {
			if (!q.categories().isEmpty() && (p.getCategoryGroup() == null
					|| Arrays.stream(p.getCategoryGroup().split(",")).map(String::strip).noneMatch(q.categories()::contains))) {
				continue;
			}
			if (!q.regionCodes().isEmpty() && !inRegion(p, q.regionCodes())) {
				continue;
			}
			if (q.age() != null && !ageFits(p, q.age())) {
				continue;
			}
			if (q.closingSoon() && (p.getApplyEndDate() == null || p.getApplyEndDate().isAfter(today.plusDays(7)))) {
				continue;
			}
			int needHits = 0;
			int score = 0;
			List<List<String>> required = q.keywords().isEmpty() ? q.audience() : q.keywords();
			for (List<String> group : required) {
				int s = score(p, group);
				needHits += s > 0 ? 1 : 0;
				score += s;
			}
			if (!required.isEmpty() && needHits == 0) {
				continue;
			}
			if (required != q.audience()) {
				for (List<String> group : q.audience()) {
					score += score(p, group);
				}
			}
			if (!q.regionCodes().isEmpty() && !p.isNationwide()) {
				score += 2;
			}
			out.add(new Scored(p, needHits, score));
		}
		return out;
	}

	/** 전국 공고이거나, 공고 지역에 그 시도·시군구가 들어 있으면 */
	static boolean inRegion(Policy p, List<String> codes) {
		if (p.isNationwide()) {
			return true;
		}
		List<String> sidos = p.getSidoCodes() == null ? List.of() : Arrays.asList(p.getSidoCodes().split(","));
		List<String> zips = p.getZipCd() == null ? List.of() : Regions.withSuccessors(Arrays.asList(p.getZipCd().split(",")));
		return codes.stream().anyMatch(c -> c.length() == 2 ? sidos.contains(c) : zips.contains(c) || zips.isEmpty() && sidos.contains(c.substring(0, 2)));
	}

	/** 공고 나이 범위(99 이상은 상한 없음)에 들면 */
	static boolean ageFits(Policy p, int age) {
		int min = p.getSprtTrgtMinAge() == null ? 0 : p.getSprtTrgtMinAge();
		int max = p.getSprtTrgtMaxAge() == null || p.getSprtTrgtMaxAge() <= 0 || p.getSprtTrgtMaxAge() >= 99 ? 200
				: p.getSprtTrgtMaxAge();
		return age >= min && age <= max;
	}

	/** 단어 묶음 하나의 점수: 묶음 안 말 중 가장 높은 자리 (제목 3, 키워드·분류 2, 본문 1, 없음 0) */
	static int score(Policy p, List<String> group) {
		String title = norm(p.getTitle());
		String tags = norm(join(p.getKeywords(), p.getCategory(), p.getMiddleCategory(), p.getCategoryGroup()));
		String body = norm(join(p.getDescription(), p.getSupportContent(), p.getAddAplyQlfcCndCn(), p.getOrganization()));
		int best = 0;
		for (String word : group) {
			String w = norm(word);
			if (!w.isEmpty()) {
				best = Math.max(best, title.contains(w) ? 3 : tags.contains(w) ? 2 : body.contains(w) ? 1 : 0);
			}
		}
		return best;
	}

	private static String join(String... parts) {
		return String.join(" ", Arrays.stream(parts).map(s -> s == null ? "" : s).toList());
	}

	private static String norm(String s) {
		return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}

	private static int order(Verdict v) {
		return switch (v) {
			case MATCH -> 0;
			case NEEDS_CHECK -> 1;
			case NO_MATCH -> 2;
		};
	}

	private static long count(List<Ranked> ranked, Verdict v) {
		return ranked.stream().filter(r -> r.item().verdict() == v).count();
	}

}
