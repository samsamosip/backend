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
			"수", "있는", "있어", "있나요", "알려줘", "찾아줘", "추천", "어떤", "뭐", "좀", "나", "저", "내가", "제가",
			"나한테", "저한테", "나에게", "저에게", "내", "조건", "맞는", "맞는거", "해줘", "추천해줘", "알려주세요");

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
		// 조건 없는 AI 답도 유효하다. "나한테 맞는 거"는 서버의 프로필 판정으로 전체 공고를 비교한다.
		if (query == null) {
			query = words(key);
		}
		else {
			cache.put(key, query);
		}
		return query;
	}

	/** AI 답 검사: 분류는 공식 5개만, 지역은 이름으로 코드를 찾을 수 있을 때만, 나이는 0~120 */
	static SearchQuery parse(JsonNode raw) {
		// A deliberate empty query is valid; missing or malformed fields are not.
		if (raw == null || !raw.isObject() || !raw.path("need").isArray() || !raw.path("who").isArray()
				|| !raw.path("categories").isArray() || !raw.path("region").isString()
				|| !raw.has("age") || !(raw.path("age").isNull() || raw.path("age").isNumber())
				|| !raw.path("closingSoon").isBoolean()) {
			throw new IllegalArgumentException("검색 조건 응답 형식이 올바르지 않습니다");
		}
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
			너는 "청년혜택 길잡이"의 검색 문장을 공고 검색 조건으로 바꾼다. 답변·공고 추천을 직접 쓰지 말고 스키마의 JSON만 반환한다.

			## 서비스와 검색 방식
			- 정부·지자체 청년 정책과 학교 장학금을 함께 찾는다. 공고에는 일상어 대신 "임차료", "구직촉진수당", "어학시험 응시료", "중개보수" 같은 행정 표현이 쓰인다.
			- 검색기는 제목·키워드·지원 내용의 부분 문자열을 비교한다. need의 어느 한 묶음만 나와도 후보가 된다. 따라서 "교육", "자금", "대출", "지원"처럼 범용 단어를 별도 묶음이나 동의어로 넣으면 무관한 공고가 섞인다.
			- categories·region·age·closingSoon은 실제로 공고를 제외하는 강한 필터다. 확실하지 않으면 비운다. 학생이라는 이유만으로 교육 분류를 붙이지 않는다.
			- 사용자 프로필은 서버가 별도로 읽어 자격을 판정한다. 너는 입력에 없는 나이·학교·지역·소득을 추측하지 않는다. 프로필 값이나 전체 공고를 요청할 필요도 없다.
			- "나한테 맞는 거", "내 조건으로 추천", "뭐 받을 수 있어", "청년 지원금 뭐 있어"처럼 분야가 없는 요청은 정상적인 전체 맞춤 검색이다. need=[], who=[], categories=[], region="", age=null, closingSoon=false가 올바른 답이다. "나한테/조건/맞는거/추천/혜택/지원금"은 키워드가 아니다.

			## 추출 규칙
			- need: 원하는 지원의 구체적인 주제 0~3개. 각 묶음은 [대표어, 실제 공고에서 쓰는 같은 뜻 표현] 최대 6개다. "월세"는 ["월세","주거비","임차료"], "면접 정장"은 ["면접 정장","면접복","정장 대여"]처럼 지원 자체를 표현한다.
			- 복합 주제의 수식어를 지킨다. "토익 응시료"를 토익 교육이나 수강료로 넓히지 말고 ["어학시험 응시료","어학시험 응시비","응시료","응시비"]로 찾는다. "전세 대출 이자"에 일반 "대출"·"융자"를 독립 묶음으로 넣지 말고 전세·주택 보증금 맥락을 유지한다.
			- "카페 창업 교육이나 자금"은 창업 맥락에서 교육·자금을 원하는 것이다. ["창업","예비창업","스타트업","사업화"]처럼 유지하고 일반 교육·자금을 따로 찾지 않는다. 실제로 서로 다른 지원을 요청하면 별도 묶음을 둔다.
			- who: 신청자 상황 0~2개. 대학생→["대학생","재학생"], 취업 준비 중→["구직","미취업","취업준비"], 결혼함→["신혼부부","기혼","혼인"]. 사람·상황은 need가 아니다. 구직 도움을 원하면 who에도 구직 상황을 남긴다.
			- 부정된 내용은 긍정 키워드로 넣지 않는다. "대학생은 아닌데"는 who에 대학생을 넣지 않고, "대출 말고 장학금"은 need에 장학금만 넣는다. 부정된 주제로 동의어를 확장하지 않는다. 이 스키마에는 제외 필터가 없으므로 제외된 공고를 완벽히 차단했다고 표현하지 않는다.
			- 나이·나이대·지역·지원/청년/정책/혜택/공고/찾아줘 같은 말은 need와 who에 넣지 않는다.
			- categories: 지원 목적이 명백할 때만 공식 분류 1개, 독립적인 두 분야면 최대 2개. 월세·전세·이사→주거, 장학·학비→교육, 취업·창업→일자리, 마음건강·문화·체육→복지문화. 어학/자격시험 응시료처럼 일자리·교육에 걸쳐 등록될 수 있는 지원은 []로 두어 누락을 막는다. 창업 교육은 일자리다.
			- region: 명시적으로 찾는 지역 이름(서울, 부산, 인천 부평구 등), 없으면 "". "나한테"나 "내 지역"만 보고 지역을 만들지 않는다.
			- age: 신청자 본인의 현재 만 나이가 명확한 경우만 정수. "30대", "청년", 부모·자녀 나이, 출생연도, "내년에 25살"처럼 미래 나이는 null. 다른 사람 나이와 본인 나이가 함께 있으면 본인 것만 쓴다.
			- closingSoon: "이번 주 마감", "마감 임박", "곧 마감"처럼 명시한 경우만 true.
			- 서비스와 무관하거나 알 수 없는 구체적인 검색어는 그 말을 need에 그대로 남긴다. 이를 전체 맞춤 요청으로 바꾸거나 다른 지원을 지어내지 않는다.

			## 입출력 예시
			입력: 나한테 맞는 혜택 추천해줘
			출력: {"need":[],"who":[],"categories":[],"region":"","age":null,"closingSoon":false}
			입력: 부산 사는 대학생인데 뭐 받을 수 있어?
			출력: {"need":[],"who":[["대학생","재학생"]],"categories":[],"region":"부산","age":null,"closingSoon":false}
			입력: 오픽 시험 접수비 좀 지원받을 수 있어?
			출력: {"need":[["어학시험 응시료","어학시험 응시비","응시료","응시비"]],"who":[],"categories":[],"region":"","age":null,"closingSoon":false}
			입력: 대출은 말고 등록금 장학금만 찾아줘
			출력: {"need":[["장학금","장학생","등록금 지원"]],"who":[],"categories":["교육"],"region":"","age":null,"closingSoon":false}
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
