package com.youthbenefit.eligibility;

import com.youthbenefit.ai.LlmClient;
import com.youthbenefit.code.OntongCodes;
import com.youthbenefit.eligibility.AiConditions.Requirement;
import com.youthbenefit.eligibility.AiConditions.Unresolved;
import com.youthbenefit.eligibility.Condition.Cmp;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.Regions;
import com.youthbenefit.profile.HousingType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 온통청년 공고의 글 조건(소득 기타, 추가 자격, 참여 제한)을 LLM 으로 판정 규칙으로 바꾼다.
 *
 * <p>
 * 안전 원칙: AI 답을 그대로 믿지 않는다. 규칙으로 들어가는 건 "만족하지 못하면 신청할 수 없는" 필수 조건뿐이고, 변수 이름·숫자 범위·코드·지역
 * 이름을 코드로 다시 검사해서 하나라도 안 맞으면 그 조건은 "원문 확인 필요"로 남긴다. 확신이 낮거나 검사에서 떨어진 게 있으면 더 큰 모델로 한 번
 * 더 본다(2차 확인).
 */
@Component
@RequiredArgsConstructor
public class AiConditionConverter {

	/** 프롬프트를 바꾸면 올린다 — 저장된 결과의 해시가 달라져서 다시 변환된다 */
	static final String PROMPT_VERSION = "v7";

	private static final int EVIDENCE_MAX = 200;

	/** 취업 상태(jobCd)는 뺐다: "취업 준비 중", "구직 희망" 같은 말이 코드(미취업자·기타)와 딱 맞지 않아 실측에서 잘못 거르는 일이 많았다 */
	private static final List<String> VARS = List.of("age", "region", "annualIncome", "householdMedianIncomePct",
			"homeowner", "housingType", "schoolCd", "mrgSttsCd");

	private static final Map<String, String> LABELS = Map.of("age", "나이", "region", "지역", "annualIncome", "소득",
			"householdMedianIncomePct", "소득", "homeowner", "주택 소유", "housingType", "주거", "jobCd", "취업 상태",
			"schoolCd", "학력", "mrgSttsCd", "결혼 여부");

	private final LlmClient llm;

	/** 변환할 글이 있는지 (소득 기타·추가 자격·참여 제한 중 하나라도) */
	public static boolean hasText(Policy p) {
		return "0043003".equals(p.getEarnCndSeCd()) || p.getAddAplyQlfcCndCn() != null || p.getPtcpPrpTrgtCn() != null;
	}

	/** 변환에 쓴 글 + 프롬프트 버전의 해시. 저장된 해시와 다르면 다시 변환한다 */
	public static String textHash(Policy p) {
		String text = String.join("\u0000", PROMPT_VERSION, String.valueOf(p.getEarnCndSeCd()),
				String.valueOf(p.getEarnEtcCn()), String.valueOf(p.getAddAplyQlfcCndCn()),
				String.valueOf(p.getPtcpPrpTrgtCn()));
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public record Converted(AiConditions conditions, boolean reviewed) {
	}

	public Converted convert(Policy p) {
		String prompt = userPrompt(p);
		Source source = Source.of(p);
		Parsed first = parse(llm.json(llm.defaultModel(), SYSTEM, prompt, "policy_conditions", SCHEMA), llm.defaultModel(),
				source);
		if (!first.needsReview() || llm.reviewModel() == null || llm.reviewModel().isBlank()) {
			return new Converted(first.conditions(), false);
		}
		Parsed second = parse(llm.json(llm.reviewModel(), SYSTEM, prompt, "policy_conditions", SCHEMA), llm.reviewModel(),
				source);
		return new Converted(second.conditions(), true);
	}

	record Parsed(AiConditions conditions, boolean needsReview) {
	}

	/** 저장해 둔 모델 답을 지금 검사 규칙으로 다시 검사한다 (AI 호출 없음) */
	public AiConditions recheck(Policy p, AiConditions stored, tools.jackson.databind.json.JsonMapper jsonMapper) {
		return parse(jsonMapper.readTree(stored.raw()), stored.model(), Source.of(p)).conditions();
	}

	/** 모델 답 → 검사한 결과. 검사에서 떨어진 조건은 unresolved 로 옮긴다. source 는 공고 원문(근거가 정말 원문에 있는지 본다). */
	static Parsed parse(JsonNode raw, String model, Source source) {
		List<Requirement> requirements = new ArrayList<>();
		List<Unresolved> unresolved = new ArrayList<>();
		boolean rejected = false;
		for (JsonNode r : raw.path("requirements")) {
			String var = r.path("var").asString("");
			Cmp cmp = cmp(r.path("op").asString(""));
			List<String> values = new ArrayList<>();
			r.path("values").forEach(v -> values.add(v.asString("").strip()));
			String evidence = trim(r.path("evidence").asString(""));
			List<Requirement> checked = cmp == null || !grounded(var, cmp, values, evidence, source) ? List.of()
					: check(var, cmp, values, evidence);
			if (checked.isEmpty()) {
				rejected = true;
				unresolved.add(new Unresolved(LABELS.getOrDefault(var, "추가 조건"), evidence, false));
			}
			requirements.addAll(checked);
		}
		for (JsonNode u : raw.path("unresolved")) {
			String label = u.path("label").asString("").strip();
			String text = trim(u.path("text").asString(""));
			if (!text.isEmpty()) {
				unresolved.add(new Unresolved(label.isEmpty() || label.length() > 15 ? "추가 조건" : label, text,
						u.path("exclusion").asBoolean(false)));
			}
		}
		unresolved = mergeSameLabel(unresolved);
		boolean none = requirements.isEmpty() && unresolved.isEmpty();
		if (none && !source.trivial()) {
			// "트랙별 상이, 공고 확인", "저소득 청년 대상"처럼 조건이 있는데 AI 가 없다고 한 경우(실측). 없다고 믿으면 "조건 일치"로 잘못
			// 나가므로, 뻔히 빈 글("해당없음", "무관")이 아니면 원문을 그대로 확인 필요로 남긴다
			unresolved.addAll(source.asUnresolved());
			none = false;
		}
		String confidence = "low".equals(raw.path("confidence").asString("")) ? "low" : "high";
		return new Parsed(new AiConditions(none, requirements, unresolved, confidence, model, raw.toString()),
				rejected || "low".equals(confidence));
	}

	/**
	 * 근거 검사: 근거 구절이 원문에 그대로 있어야 하고(지어낸 근거 막기), 변수에 맞는 말이 있어야 한다. 실측에서 나온 실수 — "연 매출 3억"을
	 * 연소득으로, "중위소득 180%"를 연소득 180만원으로, "4~12세 아동"을 신청자 나이로 읽음 — 를 여기서 막는다.
	 */
	static boolean grounded(String var, Cmp cmp, List<String> values, String evidence, Source source) {
		String e = compact(evidence.replace("…", ""));
		String all = compact(source.all());
		int at = all.indexOf(e);
		if (e.length() < 2 || at < 0) {
			return false;
		}
		// 근거 바로 앞뒤도 본다: "석사 이상" 뒤에 "혹은 준조사원"이 이어지는 경우(실측)
		String around = all.substring(Math.max(0, at - 8), Math.min(all.length(), at + e.length() + 12));
		// 참여 제한 글을 뒤집은 규칙은 틀리기 쉬워서("재학생 참여 불가" → 재학생만?) 무주택만 받는다
		boolean onlyInExclusion = source.exclusion() != null && compact(source.exclusion()).contains(e)
				&& !compact(source.all().replace(source.exclusion(), "")).contains(e);
		if (onlyInExclusion && !var.equals("homeowner")) {
			return false;
		}
		String wide = all.substring(Math.max(0, at - 30), Math.min(all.length(), at + e.length() + 30));
		// 다른 길이 있거나("인천 거주 또는 사업자 등록") 앞으로 할 일("전입하고자")이면 필수 조건이 아니다
		if (containsAny(around, "거나", "이나", "하고자", "예정", "희망", "우대", "가능")) {
			return false;
		}
		// 대상이 갈리거나("혼인 혹은 자녀출산한 가구 / 사회초년생은 19~39세") 일부에만 해당하거나("자기돌봄비에 한함") 기준에 더해지면
		// ("1인 기준 20%p 가산") 모두에게 걸리는 필수 조건이 아니다
		if (containsAny(wide, "또는", "혹은", "한함", "한하여", "한정", "가산")) {
			return false;
		}
		return switch (var) {
			// 대상마다 기준이 다르면("(청년) 5천만원, (신혼부부) 7.5천만원") 한 기준만 쓸 수 없다
			case "annualIncome" -> e.contains("소득") && !containsAny(e, "매출", "중위", "%", "퍼센트", "재산", "보증금")
					&& !all.contains("소득무관")
					&& !containsAny(wide, "부부", "신혼", "합산", "가구") && e.split("[0-9]+", -1).length <= 2;
			case "householdMedianIncomePct" -> e.contains("중위") && numbersAppear(values, evidence) && !all.contains("소득무관")
					&& loosest(cmp, values, spaceless(source.all()), "([0-9]+)%");
			case "age" -> e.contains("세") && !containsAny(e, "개월", "영아", "유아", "아동", "자녀", "초등", "아이", "학생")
					&& numbersAppear(values, evidence) && loosest(cmp, values, spaceless(source.all()), "([0-9]+)세");
			case "homeowner" -> e.contains("주택");
			// 학교·기관 소재지가 아니라 사는 곳이어야 하고, 지역 이름이 근거에 있어야 한다("도내" → 어느 도인지 지어냄)
			// "대구 외 타지역 거주"를 거꾸로 읽은 경우(실측)를 막는다
			case "region" -> containsAny(e, "거주", "주민등록", "주소", "전입", "주민")
					&& !containsAny(around, "외타", "외지역", "이외", "제외", "타지역", "타시", "외거주")
					&& values.stream().allMatch(v -> mentions(e, v));
			// 코드 이름(또는 같은 뜻의 말)이 근거에 있어야 한다("미취업자"를 미혼으로 읽은 경우를 막는다)
			case "schoolCd", "mrgSttsCd" -> values.stream().allMatch(v -> CODE_WORDS.getOrDefault(v, List.of(v)).stream()
				.map(AiConditionConverter::compact)
				.anyMatch(e::contains));
			default -> true;
		};
	}

	/** 코드 → 근거에 있어야 하는 말 (코드 이름 또는 같은 뜻). 값이 이름으로 올 때도 같다 */
	private static final Map<String, List<String>> CODE_WORDS = codeWords();

	private static Map<String, List<String>> codeWords() {
		Map<String, List<String>> words = new java.util.HashMap<>();
		Map<String, List<String>> synonyms = Map.of("0055001", List.of("기혼", "혼인", "신혼부부", "결혼한"), "0055002",
				List.of("미혼"), "0049005", List.of("대학 재학", "대학생", "재학 중인 대학", "대학에 재학"), "0049007",
				List.of("대학 졸업", "대졸", "학사"), "0049004", List.of("고교 졸업", "고등학교 졸업", "고졸"), "0049008",
				List.of("석사", "박사", "대학원"));
		java.util.function.BiConsumer<String, String> add = (code, label) -> {
			List<String> list = new ArrayList<>(synonyms.getOrDefault(code, List.of()));
			list.add(label);
			words.put(code, list);
			words.put(label, list);
		};
		OntongCodes.SCHOOL.forEach(add);
		OntongCodes.MARRIAGE.forEach(add);
		return words;
	}

	/** "충남 아산시" → 근거에 "아산"(시군구 이름 앞부분) 또는 시도 이름이 있는지 */
	private static boolean mentions(String compactEvidence, String regionName) {
		String[] tokens = regionName.strip().split("\\s+");
		String last = tokens[tokens.length - 1].replaceAll("(특별자치시|특별자치도|특별시|광역시|시|군|구|도)$", "");
		return last.length() >= 2 && compactEvidence.contains(last)
				|| tokens.length == 1 && Regions.sidoCodeOf(regionName) != null && compactEvidence.contains(regionName.strip());
	}

	/** 공고의 글 조건 세 칸 */
	record Source(String income, String additional, String exclusion) {

		static Source of(Policy p) {
			String income = "0043003".equals(p.getEarnCndSeCd())
					? (p.getEarnEtcCn() == null ? "소득 조건이 글로만 안내됨" : p.getEarnEtcCn()) : null;
			return new Source(income, p.getAddAplyQlfcCndCn(), p.getPtcpPrpTrgtCn());
		}

		String all() {
			return String.join("\n", String.valueOf(income), String.valueOf(additional), String.valueOf(exclusion));
		}

		/** 모든 칸이 비었거나 뻔히 조건 없음("해당없음", "제한없음", "무관", "미정", "-")인지 */
		boolean trivial() {
			return java.util.stream.Stream.of(income, additional, exclusion).allMatch(Source::emptyText);
		}

		private static boolean emptyText(String text) {
			if (text == null) {
				return true;
			}
			String t = compact(text);
			return t.isEmpty() || TRIVIAL.matcher(t).matches();
		}

		/** AI 를 못 믿을 때: 원래처럼 칸마다 원문 확인 필요 */
		List<Unresolved> asUnresolved() {
			List<Unresolved> out = new ArrayList<>();
			if (!emptyText(income)) {
				out.add(new Unresolved("소득 조건", trim(income), false));
			}
			if (!emptyText(additional)) {
				out.add(new Unresolved("추가 자격 조건", trim(additional), false));
			}
			if (!emptyText(exclusion)) {
				out.add(new Unresolved("참여 제한 대상", trim(exclusion), true));
			}
			return out;
		}

	}

	/** 띄어쓰기·문장부호를 뺀 뒤 이것뿐이면 조건이 없는 글 (예: "해당 없음", "제한없음", "무관, 소득별 본인부담금 차등") */
	private static final java.util.regex.Pattern TRIVIAL = java.util.regex.Pattern
		.compile("(해당없음|해당사항없음|해당무|제한없음|없음|무관|미정|소득무관|무관소득별본인부담금차등|소득제한없음)");

	/**
	 * 유형·공급별로 기준이 여럿이면(국민취업지원제도: Ⅰ유형 60%, 선발형 120%, Ⅱ유형 소득 무관) 가장 느슨한 값만 필수 조건이 될 수 있다. 원문에 나온
	 * 같은 종류 숫자 중 범위(min~max) 밖이 아니어야 하고, 이하·이상 기준은 각각 가장 큰·작은 값이어야 한다.
	 */
	private static boolean loosest(Cmp cmp, List<String> values, String compactSource, String pattern) {
		List<Long> found = java.util.regex.Pattern.compile(pattern)
			.matcher(compactSource)
			.results()
			.map(m -> Long.parseLong(m.group(1)))
			.toList();
		if (found.size() <= 1 || values.isEmpty()) {
			return true;
		}
		long max = found.stream().mapToLong(Long::longValue).max().orElseThrow();
		long min = found.stream().mapToLong(Long::longValue).min().orElseThrow();
		List<Long> v = values.stream().map(x -> Long.parseLong(x.replace(",", ""))).toList();
		return switch (cmp) {
			case BETWEEN -> v.size() == 2 && v.get(0) == min && v.get(1) == max;
			case LTE -> v.get(0) == max;
			case GTE -> v.get(0) == min;
			default -> false;
		};
	}

	/** 규칙의 숫자가 근거에 그대로 있어야 한다 (근거에 없는 "60% 이상"을 지어낸 경우를 막는다) */
	private static boolean numbersAppear(List<String> values, String evidence) {
		Set<String> numbers = new java.util.HashSet<>(Arrays.asList(evidence.split("[^0-9]+")));
		return values.stream().allMatch(v -> numbers.contains(v.replace(",", "")));
	}

	/** 같은 이름의 확인 필요 조건은 하나로 (결격사유 ①②③… 를 한 줄로) */
	private static List<Unresolved> mergeSameLabel(List<Unresolved> items) {
		Map<String, Unresolved> merged = new LinkedHashMap<>();
		for (Unresolved u : items) {
			merged.merge(u.label() + "|" + u.exclusion(), u,
					(a, b) -> new Unresolved(a.label(), trim(a.text() + " / " + b.text()), a.exclusion()));
		}
		return new ArrayList<>(merged.values());
	}

	private static boolean containsAny(String s, String... words) {
		return Arrays.stream(words).anyMatch(s::contains);
	}

	/** 숫자를 읽을 때는 문장부호(~, %)를 남긴다 */
	private static String spaceless(String s) {
		return s == null ? "" : s.replaceAll("\\s+", "");
	}

	/** 띄어쓰기·줄바꿈·문장부호 차이는 무시하고 비교 */
	private static String compact(String s) {
		return s == null ? "" : s.replaceAll("[\\s\\p{Punct}·ㆍ‧･「」『』“”‘’]", "");
	}

	/** 변수별 검사. 통과하면 판정 엔진 비교로(소득 범위는 이상·이하 둘로), 아니면 빈 목록 */
	static List<Requirement> check(String var, Cmp cmp, List<String> values, String evidence) {
		return switch (var) {
			case "age" -> numeric("age", cmp, values, 0, 120, evidence);
			case "annualIncome" -> numeric("annualIncome", cmp, values, 1, 100_000, evidence);
			case "householdMedianIncomePct" -> numeric("householdMedianIncomePct", cmp, values, 1, 1000, evidence);
			case "homeowner" -> cmp == Cmp.IN && values.size() == 1
					&& (values.get(0).equals("true") || values.get(0).equals("false"))
							? List.of(new Requirement("homeowner", Cmp.IN, values, evidence)) : List.of();
			case "housingType" -> {
				Set<String> allowed = Arrays.stream(HousingType.values()).map(Enum::name).collect(Collectors.toSet());
				yield cmp == Cmp.IN && !values.isEmpty() && allowed.containsAll(values)
						? List.of(new Requirement("housingType", Cmp.IN, List.copyOf(new LinkedHashSet<>(values)), evidence))
						: List.of();
			}
			case "schoolCd" -> codes("schoolCd", cmp, values, OntongCodes.SCHOOL, evidence);
			case "mrgSttsCd" -> codes("mrgSttsCd", cmp, values, OntongCodes.MARRIAGE, evidence);
			case "region" -> {
				if (cmp != Cmp.IN || values.isEmpty()) {
					yield List.of();
				}
				Set<String> codes = new LinkedHashSet<>();
				for (String name : values) {
					List<String> resolved = Regions.resolve(name);
					if (resolved.isEmpty()) {
						yield List.of();
					}
					codes.addAll(resolved);
				}
				yield List.of(new Requirement("zipCd", Cmp.STARTS_WITH, List.copyOf(codes), evidence));
			}
			default -> List.of();
		};
	}

	private static List<Requirement> numeric(String var, Cmp cmp, List<String> values, long min, long max,
			String evidence) {
		List<Long> numbers = new ArrayList<>();
		for (String v : values) {
			try {
				long n = Long.parseLong(v.replace(",", ""));
				if (n < min || n > max) {
					return List.of();
				}
				numbers.add(n);
			}
			catch (NumberFormatException ex) {
				return List.of();
			}
		}
		return switch (cmp) {
			case GTE, LTE -> numbers.size() == 1 ? List.of(new Requirement(var, cmp, List.of(numbers.get(0).toString()), evidence))
					: List.of();
			case BETWEEN -> {
				if (numbers.size() != 2 || numbers.get(0) > numbers.get(1)) {
					yield List.of();
				}
				// 나이는 범위 그대로, 소득은 화면 설명이 "이하/이상"이라 둘로 나눈다
				yield var.equals("age")
						? List.of(new Requirement(var, Cmp.BETWEEN, List.of(numbers.get(0).toString(), numbers.get(1).toString()), evidence))
						: List.of(new Requirement(var, Cmp.GTE, List.of(numbers.get(0).toString()), evidence),
								new Requirement(var, Cmp.LTE, List.of(numbers.get(1).toString()), evidence));
			}
			default -> List.of();
		};
	}

	/** 코드 또는 이름("미취업자")을 받아 코드로 */
	private static List<Requirement> codes(String var, Cmp cmp, List<String> values, Map<String, String> table,
			String evidence) {
		if (cmp != Cmp.IN || values.isEmpty()) {
			return List.of();
		}
		Map<String, String> byLabel = new LinkedHashMap<>();
		table.forEach((code, label) -> byLabel.put(label, code));
		Set<String> codes = new LinkedHashSet<>();
		for (String v : values) {
			String code = table.containsKey(v) ? v : byLabel.get(v);
			if (code == null) {
				return List.of();
			}
			codes.add(code);
		}
		return List.of(new Requirement(var, Cmp.IN, List.copyOf(codes), evidence));
	}

	private static Cmp cmp(String op) {
		return switch (op) {
			case "IN" -> Cmp.IN;
			case "GTE" -> Cmp.GTE;
			case "LTE" -> Cmp.LTE;
			case "BETWEEN" -> Cmp.BETWEEN;
			default -> null;
		};
	}

	private static String trim(String s) {
		String t = s.replaceAll("\\s+", " ").strip();
		return t.length() <= EVIDENCE_MAX ? t : t.substring(0, EVIDENCE_MAX) + "…";
	}

	static String userPrompt(Policy p) {
		StringBuilder b = new StringBuilder();
		b.append("[공고] ").append(p.getTitle()).append('\n');
		b.append("[이미 정리된 조건] ");
		List<String> known = ConditionEvaluator.evaluate(OntongConditions.structured(p), ProfileFacts.empty())
			.conditions()
			.stream()
			.map(c -> c.label() + ": " + c.requirement())
			.toList();
		b.append(known.isEmpty() ? "없음" : String.join(" / ", known)).append("\n\n");
		if ("0043003".equals(p.getEarnCndSeCd())) {
			b.append("[소득 조건 글]\n").append(p.getEarnEtcCn() == null ? "(소득 조건이 있다고만 표시됨)" : p.getEarnEtcCn())
				.append("\n\n");
		}
		if (p.getAddAplyQlfcCndCn() != null) {
			b.append("[추가 자격 조건 글]\n").append(p.getAddAplyQlfcCndCn()).append("\n\n");
		}
		if (p.getPtcpPrpTrgtCn() != null) {
			b.append("[참여 제한 대상 글 — 여기 해당하면 신청 불가]\n").append(p.getPtcpPrpTrgtCn()).append('\n');
		}
		return b.toString();
	}

	private static String codeTable(Map<String, String> table) {
		return table.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
	}

	static final String SYSTEM = """
			너는 한국 청년 정책 공고의 자격 조건 글을 판정 규칙으로 바꾸는 도우미다. 사용자의 프로필과 비교해 자동으로 신청 가능 여부를 가릴 때 쓴다.
			틀린 규칙은 신청할 수 있는 사람을 "불가"로 잘못 걸러내므로, 확실한 것만 규칙으로 만든다.

			## requirements (규칙)
			- "이 조건을 만족하지 못하면 절대 신청할 수 없는" 필수 조건만 넣는다.
			- 신청자 본인에 대한 조건만 규칙으로 쓴다. 자녀·영아·부모·배우자·가구원·사업체(매출, 업력, 업종)·물건(보증금, 월세, 주택 가격)에 대한 조건은 unresolved.
			- [이미 정리된 조건]과 같은 내용은 다시 쓰지 않는다. 글에서 새로 알게 된 조건만 쓴다.
			- 다른 길이 있으면 넣지 않는다: "~하거나", "또는", "우대", "가점", "우선", "권장", "중심", "생활권", "활동하는", "예정자 포함" 등 → unresolved.
			- 쓸 수 있는 변수(var)와 연산(op), 값(values, 모두 문자열):
			  - age: 신청일 기준 만 나이. GTE/LTE ["19"], BETWEEN ["19","39"]. 출생연도 범위는 나이로 바꾸지 말고 unresolved.
			  - region: 신청자가 주민등록·거주해야 하는 지역. op IN, values 는 지역 이름 목록 ["충남 아산시"], ["서울"], ["인천 부평구"]. 시도+시군구로 쓴다. 읍·면·동, "관내"처럼 이름이 없는 곳은 unresolved.
			  - annualIncome: 본인 연소득(만원). "연소득 6천만원 이하" → LTE ["6000"]. 매출·재산·보증금은 소득이 아니다. 기준중위소득 %는 annualIncome 이 아니라 householdMedianIncomePct. 부부합산·가구 기준이 함께 있거나 월소득만 있으면 unresolved.
			  - householdMedianIncomePct: 가구 기준중위소득 %. LTE ["150"], BETWEEN ["60","160"]. 도시근로자 월평균소득 % 는 다른 기준이라 unresolved.
			  - homeowner: 본인 주택 소유. "무주택"이면 IN ["false"]. "세대원 전원 무주택", "무주택 세대주"처럼 가구 조건이면 homeowner ["false"] 와 함께 unresolved 에도 가구 조건을 남긴다.
			  - housingType: 지금 사는 형태. IN 중 골라서: MONTHLY_RENT(월세), JEONSE(전세), OWN(자가), WITH_PARENTS(부모님과 거주).
			  - schoolCd: 학력 코드. IN. {SCHOOL}
			  - mrgSttsCd: 결혼 여부 코드. IN. {MARRIAGE}
			- 취업 상태(재직·구직·창업 등)는 규칙으로 쓰지 말고 unresolved.
			- 참여 제한(제외) 글의 조건은 반대로 바꿔 필수 조건으로 쓴다: "주택 소유자 제외" → homeowner IN ["false"]. 바꿀 수 없으면 unresolved 에 exclusion=true.
			- evidence 에는 근거가 된 원문 구절을 한 글자도 바꾸지 말고 그대로 옮긴다(10~40자). 원문에 없는 말을 쓰면 그 규칙은 버려진다.

			## unresolved (판정 못 하는 조건 — 사람이 원문을 확인)
			- 위 변수로 표현할 수 없는 실제 조건: 직업·자격증·활동 이력·가족 상황·사업자 요건·병역·장애 등.
			- label: 화면에 보일 짧은 이름(10자 이내, 예: "세대 무주택", "예술인", "중소기업 재직"). text: 원문 요약(원문 표현 위주, 100자 이내).
			- exclusion: 참여 제한(해당하면 신청 불가) 조건이면 true.

			## 버리는 것 (조건이 아님)
			- "해당없음", "제한없음", "무관", "-", 문의 안내("읍면사무소 문의"), 지원 내용·금액·절차·서류·예산 소진 안내, 이미 정리된 조건과 같은 말.

			## 나머지
			- noExtraConditions: 글에 실제 조건이 하나도 없으면 true.
			- confidence: 해석이 확실하면 "high", 애매하거나 글이 잘려 있으면 "low".
			""".replace("{SCHOOL}", codeTable(OntongCodes.SCHOOL))
		.replace("{MARRIAGE}", codeTable(OntongCodes.MARRIAGE));

	static final Map<String, Object> SCHEMA = Map.of("type", "object", "additionalProperties", false, "required",
			List.of("noExtraConditions", "requirements", "unresolved", "confidence"), "properties",
			Map.of("noExtraConditions", Map.of("type", "boolean"), "requirements",
					Map.of("type", "array", "items", Map.of("type", "object", "additionalProperties", false, "required",
							List.of("var", "op", "values", "evidence"), "properties",
							Map.of("var", Map.of("type", "string", "enum", VARS), "op",
									Map.of("type", "string", "enum", List.of("IN", "GTE", "LTE", "BETWEEN")), "values",
									Map.of("type", "array", "items", Map.of("type", "string")), "evidence",
									Map.of("type", "string")))),
					"unresolved",
					Map.of("type", "array", "items", Map.of("type", "object", "additionalProperties", false, "required",
							List.of("label", "text", "exclusion"), "properties",
							Map.of("label", Map.of("type", "string"), "text", Map.of("type", "string"), "exclusion",
									Map.of("type", "boolean")))),
					"confidence", Map.of("type", "string", "enum", List.of("high", "low"))));

}
