package com.youthbenefit.support;

import com.youthbenefit.ai.LlmClient;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.Support;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 지원 내용 글 → 지원 금액(1인당 받는 돈)과 한 줄 요약. AI 답은 코드로 검사한다: 금액은 근거 구절에 실제로 적힌 금액 중 하나여야 하고(5백만원 =
 * 5,000,000), 근거는 원문에 있어야 하고, 사업 예산·기업 지원액이면 버린다. 틀린 금액을 보여 주느니 안 보여 준다(요약만).
 */
@Component
@RequiredArgsConstructor
public class AiSupportExtractor {

	private static final long MIN_WON = 1_000;

	private static final long MAX_WON = 1_000_000_000;

	private final LlmClient llm;

	public Map<String, Object> extract(Policy p) {
		JsonNode raw = llm.json(llm.defaultModel(), SYSTEM, userPrompt(p), "policy_support", SCHEMA);
		return check(raw, sourceText(p), llm.defaultModel());
	}

	/** 저장할 JSON 모양: amountWon·period·upTo·kind(검사 통과한 것만), summary, evidence, model, raw */
	static Map<String, Object> check(JsonNode raw, String source, String model) {
		Map<String, Object> out = new LinkedHashMap<>();
		String evidence = raw.path("evidence").asString("").strip();
		String kind = raw.path("kind").asString("OTHER");
		Long amount = raw.path("amountWon").isNumber() ? raw.path("amountWon").asLong() : null;
		String period = raw.path("period").asString("");
		// 근거에 금액이 하나만 적혀 있는데 AI 숫자가 다르면 원문 숫자를 쓴다 (실측: "연60백만원"을 600만원으로 읽음)
		List<Long> written = Support.amounts(evidence).stream().distinct().toList();
		if (amount != null && written.size() == 1 && !written.contains(amount)) {
			amount = written.get(0);
		}
		if (amount != null && !amountOk(amount, evidence, source, kind)) {
			amount = null;
		}
		if (amount != null) {
			List<Long> inEvidence = Support.amounts(evidence);
			boolean tiers = inEvidence.stream().distinct().count() > 1;
			out.put("amountWon", amount);
			out.put("period", periodOk(period, evidence) ? period : null);
			// 구간별로 다르면("30만원/20만원/10만원") 가장 큰 값을 "최대"로
			out.put("upTo", raw.path("upTo").asBoolean(false) || tiers || containsAny(evidence, "최대", "이내", "한도"));
			out.put("kind", kind);
		}
		else {
			out.put("kind", "SERVICE".equals(kind) ? "SERVICE" : "OTHER");
		}
		String summary = raw.path("summary").asString("").strip();
		out.put("summary", summary.isEmpty() || summary.length() > 20 ? null : summary);
		out.put("evidence", evidence);
		out.put("model", model);
		out.put("raw", raw.toString());
		return out;
	}

	private static boolean amountOk(long amount, String evidence, String source, String kind) {
		if (amount < MIN_WON || amount > MAX_WON || "SERVICE".equals(kind)) {
			return false;
		}
		String e = compact(evidence);
		if (e.length() < 3 || !compact(source).contains(e)) {
			return false;
		}
		// 사업 전체 예산·기업 지원액·보험 보장 한도는 개인이 받는 돈이 아니다
		if (containsAny(e, "예산", "사업비", "총사업", "개사", "기업당", "기관당", "단체당", "억원투입", "식지원", "보험", "보장")) {
			return false;
		}
		return Support.amounts(evidence).contains(amount);
	}

	/** 주기 말이 근거에 있어야 한다 */
	private static boolean periodOk(String period, String evidence) {
		return switch (period) {
			case "MONTH" -> containsAny(evidence, "월", "매월", "달");
			case "YEAR" -> containsAny(evidence, "연", "년", "연간");
			case "SEMESTER" -> evidence.contains("학기");
			case "ONCE" -> containsAny(evidence, "1회", "일시", "1쌍당", "1인당", "회");
			case "TOTAL" -> containsAny(evidence, "총", "최대", "누적");
			default -> false;
		};
	}

	private static boolean containsAny(String s, String... words) {
		for (String w : words) {
			if (s.contains(w)) {
				return true;
			}
		}
		return false;
	}

	private static String compact(String s) {
		return s == null ? "" : s.replaceAll("[\\s\\p{Punct}·ㆍ‧･「」『』“”‘’]", "");
	}

	static String sourceText(Policy p) {
		return String.valueOf(p.getSupportContent()) + "\n" + String.valueOf(p.getDescription());
	}

	static String userPrompt(Policy p) {
		return "[공고] " + p.getTitle() + "\n\n[지원 내용]\n" + (p.getSupportContent() == null ? "(없음)" : p.getSupportContent())
				+ "\n\n[설명]\n" + (p.getDescription() == null ? "(없음)" : p.getDescription());
	}

	static final String SYSTEM = """
			너는 청년 정책 공고에서 "신청한 사람 한 명이 받는 지원"을 뽑아 앱 카드에 한 줄로 보여 주는 도우미다.

			- amountWon: 한 사람(또는 한 가구·한 쌍)이 받는 돈의 액수(원, 정수). 여러 구간이면 가장 큰 값. 돈이 아니거나(교육·상담·행사) 글에 액수가 없으면 null.
			  사업 전체 예산, 기업·기관이 받는 돈, 선정 인원, 금리(%)는 액수가 아니다. 이자 지원·대출은 한도 액수를 쓴다.
			  예: "최대 월 30만원" → 300000, "5백만원" → 5000000, "1,000만원(1회)" → 10000000.
			- period: MONTH(매월), YEAR(연간), SEMESTER(학기당), ONCE(한 번), TOTAL(기간 합계), 모르면 "".
			- upTo: "최대", "이내", "한도", 구간별 차등이면 true.
			- kind: CASH(현금·수당·장려금), LOAN(대출), INTEREST(이자 지원), VOUCHER(바우처·이용권·물품), SERVICE(교육·상담·행사·공간 등 돈이 아님), OTHER.
			- evidence: 액수가 적힌 원문 구절을 한 글자도 바꾸지 말고 그대로(10~40자). 액수가 없으면 "".
			- summary: 무엇을 받는지 15자 이내 명사구, 원문 표현 위주. 예: "월세 지원", "창업 교육·컨설팅", "면접 정장 대여", "자립수당".
			""";

	static final Map<String, Object> SCHEMA = Map.of("type", "object", "additionalProperties", false, "required",
			List.of("amountWon", "period", "upTo", "kind", "evidence", "summary"), "properties",
			Map.of("amountWon", Map.of("type", List.of("integer", "null")), "period",
					Map.of("type", "string", "enum", List.of("MONTH", "YEAR", "SEMESTER", "ONCE", "TOTAL", "")), "upTo",
					Map.of("type", "boolean"), "kind",
					Map.of("type", "string", "enum", List.of("CASH", "LOAN", "INTEREST", "VOUCHER", "SERVICE", "OTHER")),
					"evidence", Map.of("type", "string"), "summary", Map.of("type", "string")));

}
