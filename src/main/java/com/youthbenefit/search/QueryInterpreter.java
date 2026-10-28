package com.youthbenefit.search;

import com.youthbenefit.ai.LlmClient;
import com.youthbenefit.policy.Categories;
import com.youthbenefit.policy.Regions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 검색 문장 → {@link SearchQuery}. AI 는 문장을 알아듣는 데만 쓰고 공고는 코드가 찾는다. AI 가 안 되면(설정 없음·오류) 띄어쓰기로 나눈
 * 단어로 찾는다 — 검색이 멈추지 않게. 같은 문장은 해석을 기억해 둔다(빠르고 비용 없음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryInterpreter {

	private static final int CACHE_SIZE = 500;

	/** 단어 검색 때 버리는 말 (어디에나 나와서 거르는 힘이 없음) */
	private static final Set<String> STOP_WORDS = Set.of("지원", "지원금", "사업", "청년", "정책", "혜택", "공고", "신청", "받을",
			"수", "있는", "있어", "있나요", "알려줘", "찾아줘", "추천", "어떤", "뭐", "좀", "나", "저", "내가", "제가");

	private final LlmClient llm;

	private final Map<String, SearchQuery> cache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, SearchQuery> eldest) {
			return size() > CACHE_SIZE;
		}
	});

	public SearchQuery interpret(String text) {
		return interpret(text, true);
	}

	/** @param allowAi false 면(사용량 제한) 기억해 둔 해석이 없을 때 단어로만 찾는다 */
	public SearchQuery interpret(String text, boolean allowAi) {
		String key = text.strip().replaceAll("\\s+", " ");
		SearchQuery cached = cache.get(key);
		if (cached != null) {
			return cached;
		}
		SearchQuery query = null;
		if (allowAi && llm.configured() && !llm.overDailyLimit()) {
			try {
				query = parse(llm.json(llm.defaultModel(), SYSTEM, key, "search_query", SCHEMA));
			}
			catch (RuntimeException ex) {
				log.warn("검색 문장 해석 실패, 단어로 찾음: {}", ex.getMessage());
			}
		}
		if (query == null || !query.hasFilters()) {
			query = words(key);
		}
		else {
			cache.put(key, query);
		}
		return query;
	}

	/** AI 답 검사: 분류는 공식 5개만, 지역은 이름으로 코드를 찾을 수 있을 때만, 나이는 0~120 */
	static SearchQuery parse(JsonNode raw) {
		List<List<String>> keywords = groups(raw.path("need"));
		List<List<String>> audience = groups(raw.path("who"));
		List<String> categories = new ArrayList<>();
		raw.path("categories").forEach(c -> {
			String s = c.asString("");
			if (Categories.OFFICIAL.contains(s) && !categories.contains(s)) {
				categories.add(s);
			}
		});
		String region = raw.path("region").asString("").strip();
		List<String> codes = region.isEmpty() ? List.of() : Regions.resolve(region);
		Integer age = raw.path("age").isNumber() && raw.path("age").asInt() > 0 && raw.path("age").asInt() <= 120
				? raw.path("age").asInt() : null;
		return new SearchQuery(keywords, audience, categories.subList(0, Math.min(2, categories.size())),
				codes.isEmpty() ? null : region, codes, age, raw.path("closingSoon").asBoolean(false), true, false);
	}

	/** 단어 묶음 검사: 흔한 말("지원", "청년")은 빼고, 대표어가 흔한 말이면 묶음째 뺀다 */
	private static List<List<String>> groups(JsonNode node) {
		List<List<String>> out = new ArrayList<>();
		for (JsonNode group : node) {
			List<String> words = new ArrayList<>();
			group.forEach(w -> {
				String s = w.asString("").strip();
				if (!s.isEmpty() && s.length() <= 20 && !words.contains(s)) {
					words.add(s);
				}
			});
			if (words.isEmpty() || STOP_WORDS.contains(words.get(0))) {
				continue;
			}
			words.removeIf(STOP_WORDS::contains);
			if (!words.isEmpty() && out.size() < 4) {
				out.add(List.copyOf(words.subList(0, Math.min(6, words.size()))));
			}
		}
		return out;
	}

	/** AI 없이: 띄어쓰기로 나눈 단어(흔한 말은 뺌), 지역 이름이면 지역으로 */
	static SearchQuery words(String text) {
		List<List<String>> keywords = new ArrayList<>();
		String region = null;
		List<String> codes = List.of();
		for (String token : text.split("[\\s,./?!]+")) {
			String w = token.replaceAll("(에서|에|을|를|은|는|이|가|의|도|만|으로|로|하는|사는|살고)$", "");
			if (w.length() < 2 || STOP_WORDS.contains(w)) {
				continue;
			}
			List<String> resolved = codes.isEmpty() ? Regions.resolve(w) : List.of();
			if (!resolved.isEmpty()) {
				region = w;
				codes = resolved;
				continue;
			}
			if (keywords.size() < 5) {
				keywords.add(List.of(w));
			}
		}
		List<String> categories = Arrays.stream(text.split("\\s+"))
			.filter(Categories.OFFICIAL::contains)
			.distinct()
			.toList();
		keywords.removeIf(g -> categories.contains(g.get(0)));
		return new SearchQuery(keywords, List.of(), categories, region, codes, null, false, false, false);
	}

	static final String SYSTEM = """
			너는 청년 정책 검색창에 들어온 문장을 검색 조건으로 바꾸는 도우미다. 공고 제목·설명에서 단어를 찾아 검색한다.

			- need: 받고 싶은 지원·혜택(무엇이 필요한지) 단어 묶음 1~3개. 묶음마다 [대표어, 공고에 쓰일 같은 뜻 말 2~5개].
			  예: "월세" → ["월세", "주거비", "임차료", "월 임대료"], "전세 대출" → ["전세", "임차보증금", "보증금"] 과 ["대출", "융자", "이자"],
			  "학원비" → ["학원비", "수강료", "교육비", "훈련비"], "창업" → ["창업", "스타트업", "예비창업"].
			- who: 신청하는 사람이 누구인지(대학생, 취준생, 신혼부부, 1인가구 등) 단어 묶음 0~2개. 예: "대학생" → ["대학생", "재학생"],
			  "취업 준비" → ["구직", "미취업", "취업준비"]. 사람을 나타내는 말은 need 가 아니라 who 에 넣는다.
			- 필요한 것 없이 상황만 말하면("결혼했는데 받을 수 있는 거", "대학생인데 뭐 있어") need 는 비우고 who 에 넣는다.
			  예: "결혼했는데" → who ["신혼부부", "기혼", "결혼", "혼인"].
			- need·who 에 지역·나이·"지원", "청년", "정책", "혜택", "알려줘" 같은 말은 넣지 않는다.
			- categories: need 기준으로 분명할 때만 1개(많아야 2개): 일자리, 주거, 교육, 복지문화, 참여권리. 예: 월세·전세·이사 → 주거, 취업·창업 → 일자리, 학비·장학 → 교육.
			- region: 지역이 있으면 이름("서울", "부산", "인천 부평구", "충남 아산시"), 없으면 "".
			- age: "24살", "만 29세"처럼 나이가 있으면 숫자, 없으면 null.
			- closingSoon: "마감 임박", "곧 마감", "이번 주 마감"이면 true.
			""";

	static final Map<String, Object> SCHEMA = Map.of("type", "object", "additionalProperties", false, "required",
			List.of("need", "who", "categories", "region", "age", "closingSoon"), "properties",
			Map.of("need", Map.of("type", "array", "items", Map.of("type", "array", "items", Map.of("type", "string"))), "who",
					Map.of("type", "array", "items", Map.of("type", "array", "items", Map.of("type", "string"))),
					"categories",
					Map.of("type", "array", "items", Map.of("type", "string", "enum", Categories.OFFICIAL)), "region",
					Map.of("type", "string"), "age", Map.of("type", List.of("integer", "null")), "closingSoon",
					Map.of("type", "boolean")));

}
