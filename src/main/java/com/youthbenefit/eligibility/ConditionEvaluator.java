package com.youthbenefit.eligibility;

import com.youthbenefit.code.OntongCodes;
import com.youthbenefit.eligibility.Condition.All;
import com.youthbenefit.eligibility.Condition.Any;
import com.youthbenefit.eligibility.Condition.Check;
import com.youthbenefit.eligibility.Condition.Cmp;
import com.youthbenefit.eligibility.Condition.Not;
import com.youthbenefit.eligibility.Condition.Unparsed;
import com.youthbenefit.policy.Regions;
import com.youthbenefit.profile.HousingType;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 조건 나무를 사용자 값으로 판정하고, 조건마다 사람이 읽을 수 있는 설명을 만든다. */
public final class ConditionEvaluator {

	private static final Map<String, String> LABELS = Map.ofEntries(Map.entry("age", "나이"),
			Map.entry("zipCd", "지역"), Map.entry("annualIncome", "소득"),
			Map.entry("householdMedianIncomePct", "소득"), Map.entry("jobCd", "취업 상태"),
			Map.entry("schoolCd", "학력"), Map.entry("mrgSttsCd", "결혼 여부"), Map.entry("plcyMajorCd", "전공"),
			Map.entry("sbizCd", "특화 대상"), Map.entry("housingType", "주거"), Map.entry("homeowner", "주택 소유"));

	private ConditionEvaluator() {
	}

	public record Evaluation(Truth truth, List<ConditionResult> conditions) {
	}

	public static Evaluation evaluate(Condition root, ProfileFacts facts) {
		List<ConditionResult> results = new ArrayList<>();
		Truth truth = eval(root, facts, results, false);
		return new Evaluation(truth, results);
	}

	private static Truth eval(Condition c, ProfileFacts facts, List<ConditionResult> out, boolean negated) {
		return switch (c) {
			case All all -> Truth.and(all.children().stream().map(ch -> eval(ch, facts, out, negated)).toList());
			case Any any -> Truth.or(any.children().stream().map(ch -> eval(ch, facts, out, negated)).toList());
			case Not not -> eval(not.child(), facts, out, !negated).not();
			case Unparsed u -> {
				out.add(new ConditionResult(u.label(), Truth.UNKNOWN, negated ? "해당하면 제외 — 원문 확인 필요" : "원문 확인 필요",
						null, null, u.evidence()));
				yield Truth.UNKNOWN;
			}
			case Check check -> {
				Object mine = facts.get(check.var());
				Truth t = mine == null ? Truth.UNKNOWN : compare(check, mine);
				Truth shown = negated ? t.not() : t;
				out.add(new ConditionResult(LABELS.getOrDefault(check.var(), check.var()), shown,
						(negated ? "제외: " : "") + describe(check), mine == null ? null : describeValue(check.var(), mine),
						mine == null ? check.var() : null, check.evidence()));
				yield t;
			}
		};
	}

	static Truth compare(Check c, Object mine) {
		List<String> v = c.values();
		return switch (c.cmp()) {
			case BETWEEN -> Truth.of(number(mine) >= Long.parseLong(v.get(0)) && number(mine) <= Long.parseLong(v.get(1)));
			case GTE -> Truth.of(number(mine) >= Long.parseLong(v.get(0)));
			case LTE -> Truth.of(number(mine) <= Long.parseLong(v.get(0)));
			case IN -> Truth.of(v.contains(mine.toString()));
			case INTERSECTS -> Truth.of(((Collection<?>) mine).stream().map(Object::toString).anyMatch(v::contains));
		};
	}

	private static long number(Object value) {
		return ((Number) value).longValue();
	}

	static String describe(Check c) {
		List<String> v = c.values();
		return switch (c.var()) {
			case "age" -> switch (c.cmp()) {
				case BETWEEN -> "만 " + v.get(0) + "~" + v.get(1) + "세";
				case GTE -> "만 " + v.get(0) + "세 이상";
				case LTE -> "만 " + v.get(0) + "세 이하";
				default -> v.toString();
			};
			case "zipCd" -> String.join("·", Regions.displayNames(false, Regions.sidoCodes(String.join(",", v))))
					+ " 주민등록 (시군구 " + v.size() + "곳)";
			case "annualIncome" -> "연소득 " + won(v.get(0)) + (c.cmp() == Cmp.LTE ? " 이하" : " 이상");
			case "householdMedianIncomePct" -> "가구 기준중위소득 " + v.get(0) + "%" + (c.cmp() == Cmp.LTE ? " 이하" : " 이상");
			default -> String.join(", ", v.stream().map(code -> label(c.var(), code)).toList());
		};
	}

	static String describeValue(String var, Object mine) {
		return switch (var) {
			case "age" -> "만 " + mine + "세";
			case "zipCd" -> String.join("", Regions.displayNames(false, Regions.sidoCodes(mine.toString()))) + " (" + mine
					+ ")";
			case "annualIncome" -> "연소득 " + won(mine.toString());
			case "householdMedianIncomePct" -> "기준중위소득 " + mine + "%";
			case "sbizCd" -> {
				List<String> codes = ((Collection<?>) mine).stream().map(Object::toString).toList();
				yield codes.isEmpty() ? "해당 없음" : String.join(", ", codes.stream().map(x -> label(var, x)).toList());
			}
			default -> label(var, mine.toString());
		};
	}

	private static String label(String var, String code) {
		Map<String, String> codes = switch (var) {
			case "jobCd" -> OntongCodes.JOB;
			case "schoolCd" -> OntongCodes.SCHOOL;
			case "mrgSttsCd" -> OntongCodes.MARRIAGE;
			case "plcyMajorCd" -> OntongCodes.MAJOR;
			case "sbizCd" -> OntongCodes.SPECIAL_GROUP;
			default -> Map.of();
		};
		if (var.equals("housingType")) {
			try {
				return HousingType.valueOf(code).label();
			}
			catch (IllegalArgumentException ex) {
				return code;
			}
		}
		return codes.getOrDefault(code, code);
	}

	private static String won(String tenThousands) {
		return NumberFormat.getNumberInstance(Locale.KOREA).format(Long.parseLong(tenThousands)) + "만원";
	}

}
