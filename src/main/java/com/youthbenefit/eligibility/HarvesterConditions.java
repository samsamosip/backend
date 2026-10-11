package com.youthbenefit.eligibility;

import com.youthbenefit.eligibility.Condition.All;
import com.youthbenefit.eligibility.Condition.Any;
import com.youthbenefit.eligibility.Condition.Check;
import com.youthbenefit.eligibility.Condition.Cmp;
import com.youthbenefit.eligibility.Condition.Unparsed;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.Regions;
import com.youthbenefit.profile.EnrollmentStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 학교 장학 공고(policy-harvester)의 자격 정보(eligibility)를 조건 나무로 바꾼다.
 * Harvester 는 항목마다 *_rule_status 를 준다: restricted(조건 있음) / unrestricted(제한 없음) / unknown(못 정함).
 * restricted 이고 값이 확실할 때만 Check 를 만들고, 나머지 글 조건(residual_conditions_text)은 Unparsed(미확인)로 둔다.
 */
public final class HarvesterConditions {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String RESTRICTED = "restricted";

	/** "국내 4년제 대학", "울산시 소재 고등학교" 처럼 학교 하나를 가리키지 않는 이름 */
	private static final Pattern GENERIC_SCHOOL = Pattern
		.compile("국내|소재|지원\\s*대학|참여\\s*대학|해당하는|고등교육법|4년제|전문대학|교육대학|^대학(교)?$|고등학교");

	private HarvesterConditions() {
	}

	public static Condition from(Policy p) {
		JsonNode e = JSON.readTree(p.getRawPayload()).path("eligibility");
		if (e.isMissingNode() || e.isNull()) {
			return new All(List.of(new Unparsed("자격 조건", "자격 조건이 정리되지 않았어요. 공고 원문을 확인하세요")));
		}
		List<Condition> children = new ArrayList<>();
		academicLevel(e, children);
		enrollment(e, children);
		grade(e, children);
		age(e, children);
		gpa(e, children);
		income(e, children);
		residency(e, children);
		university(e, children);
		String residual = text(e, "residual_conditions_text");
		if (residual != null) {
			children.add(new Unparsed("기타 자격 조건", residual));
		}
		return new All(children);
	}

	/**
	 * 학생 구분은 사용자의 학력 코드(schoolCd)로 본다. 입학 예정자(incoming)를 받는 장학은 고교 졸업(예정)·대학 졸업도 인정한다.
	 */
	private static void academicLevel(JsonNode e, List<Condition> out) {
		if (!restricted(e, "academic_level_rule_status")) {
			return;
		}
		boolean incoming = strings(e.path("enrollment_states")).contains("incoming");
		Set<String> codes = new LinkedHashSet<>();
		boolean other = false;
		for (String level : strings(e.path("academic_levels"))) {
			switch (level) {
				case "elementary", "middle" -> codes.add("0049001");
				case "high" -> codes.addAll(List.of("0049002", "0049003"));
				case "undergraduate" -> {
					codes.addAll(List.of("0049005", "0049006"));
					if (incoming) {
						codes.addAll(List.of("0049003", "0049004"));
					}
				}
				case "graduate" -> {
					codes.add("0049008");
					if (incoming) {
						codes.addAll(List.of("0049006", "0049007"));
					}
				}
				default -> other = true;
			}
		}
		addWithOther(out, codes.isEmpty() ? null : new Check("schoolCd", Cmp.IN, List.copyOf(codes),
				"academic_levels=" + strings(e.path("academic_levels"))), other, "학생 구분");
	}

	private static void enrollment(JsonNode e, List<Condition> out) {
		if (!restricted(e, "enrollment_rule_status")) {
			return;
		}
		List<String> states = strings(e.path("enrollment_states"));
		List<String> mapped = states.stream()
			.map(EnrollmentStatus::fromHarvester)
			.filter(Objects::nonNull)
			.map(Enum::name)
			.distinct()
			.toList();
		boolean other = mapped.size() < states.size();
		addWithOther(out, mapped.isEmpty() ? null
				: new Check("enrollmentStatus", Cmp.IN, mapped, "enrollment_states=" + states), other, "학적");
	}

	/** 학년은 rule_status 가 따로 없고 grade_min/max 가 있으면 조건이다. */
	private static void grade(JsonNode e, List<Condition> out) {
		range(out, "gradeLevel", integer(e, "grade_min"), integer(e, "grade_max"));
	}

	private static void age(JsonNode e, List<Condition> out) {
		if (restricted(e, "age_rule_status")) {
			range(out, "age", integer(e, "age_min"), integer(e, "age_max"));
		}
	}

	private static void gpa(JsonNode e, List<Condition> out) {
		if (!restricted(e, "gpa_rule_status")) {
			return;
		}
		BigDecimal threshold = decimal(e, "gpa_threshold");
		BigDecimal scale = decimal(e, "gpa_scale");
		String operator = text(e, "gpa_operator");
		if (threshold != null && scale != null && (">=".equals(operator) || ">".equals(operator))) {
			// 4.5 만점의 3.0 은 "3.0" 으로, 100 점 만점의 80.0 은 "80" 으로 보여 준다
			String min = scale.compareTo(BigDecimal.TEN) < 0
					? threshold.setScale(Math.max(1, threshold.stripTrailingZeros().scale())).toPlainString()
					: plain(threshold);
			out.add(new Check("gpa", Cmp.GTE, List.of(min, plain(scale)),
					"gpa " + operator + " " + plain(threshold) + " / " + plain(scale)));
		}
		else {
			out.add(new Unparsed("성적", "성적 기준이 있어요. 공고 원문을 확인하세요"));
		}
	}

	/** 소득은 한국장학재단 학자금 지원구간(kosaf_support_bracket)만 비교한다. */
	private static void income(JsonNode e, List<Condition> out) {
		if (!restricted(e, "income_rule_status")) {
			return;
		}
		if ("kosaf_support_bracket".equals(text(e, "income_metric"))) {
			BigDecimal max = decimal(e, "income_max");
			BigDecimal min = decimal(e, "income_min");
			if (max != null) {
				out.add(new Check("kosafIncomeBracket", Cmp.LTE, List.of(String.valueOf(max.intValue())),
						"income_max=" + plain(max)));
			}
			if (min != null) {
				out.add(new Check("kosafIncomeBracket", Cmp.GTE, List.of(String.valueOf(min.intValue())),
						"income_min=" + plain(min)));
			}
			if (max != null || min != null) {
				return;
			}
		}
		out.add(new Unparsed("소득", "소득 기준이 있어요. 공고 원문을 확인하세요"));
	}

	/**
	 * 거주지 이름("인천", "춘천시", "울산 남구", "용인시")을 시도·시군구 코드로 바꿔 주민등록 주소와 비교한다.
	 * 동·읍·면 단위이거나 여러 곳에 있는 이름("남구")이면 정할 수 없어 미확인.
	 * 보호자 거주도 인정하는 경우(self_or_parent) 내 주소가 달라도 보호자 주소가 맞을 수 있어 "불일치" 대신 미확인이 된다.
	 */
	private static void residency(JsonNode e, List<Condition> out) {
		if (!restricted(e, "residency_rule_status")) {
			return;
		}
		List<String> regions = strings(e.path("residency_region_codes"));
		String place = String.join(", ", regions);
		boolean parent = "self_or_parent".equals(text(e, "residency_subject"));
		List<List<String>> resolved = regions.stream().map(Regions::resolve).toList();
		if (regions.isEmpty() || resolved.stream().anyMatch(List::isEmpty)) {
			out.add(new Unparsed("거주지", (place.isEmpty() ? "거주지 조건" : place + " 거주")
					+ (parent ? " (본인 또는 보호자)" : "")));
			return;
		}
		List<String> prefixes = resolved.stream().flatMap(List::stream).distinct().toList();
		Check check = new Check("zipCd", Cmp.STARTS_WITH, prefixes, "residency_region_codes=" + regions);
		out.add(parent ? new Any(List.of(check, new Unparsed("보호자 거주지", place + " 거주 보호자도 인정"))) : check);
	}

	private static void university(JsonNode e, List<Condition> out) {
		if (!restricted(e, "university_rule_status")) {
			return;
		}
		List<String> names = strings(e.path("eligible_universities"));
		if (names.isEmpty() || names.stream().anyMatch(n -> GENERIC_SCHOOL.matcher(n).find())) {
			out.add(new Unparsed("학교", names.isEmpty() ? "대상 학교가 정해져 있어요" : String.join(", ", names)));
			return;
		}
		out.add(new Check("schoolName", Cmp.IN, names, "eligible_universities=" + names));
	}

	/** 목록에 우리가 표현 못 하는 값(other)이 섞여 있으면: 내 값이 목록에 있으면 참, 아니면 미확인. */
	private static void addWithOther(List<Condition> out, Check check, boolean other, String label) {
		if (check == null) {
			out.add(new Unparsed(label, label + " 조건이 있어요. 공고 원문을 확인하세요"));
		}
		else if (other) {
			out.add(new Any(List.of(check, new Unparsed(label, "그 밖의 " + label + "도 인정될 수 있어요"))));
		}
		else {
			out.add(check);
		}
	}

	private static void range(List<Condition> out, String var, Integer min, Integer max) {
		String evidence = var + " " + min + "~" + max;
		if (min != null && max != null) {
			out.add(new Check(var, Cmp.BETWEEN, List.of(String.valueOf(min), String.valueOf(max)), evidence));
		}
		else if (min != null) {
			out.add(new Check(var, Cmp.GTE, List.of(String.valueOf(min)), evidence));
		}
		else if (max != null) {
			out.add(new Check(var, Cmp.LTE, List.of(String.valueOf(max)), evidence));
		}
	}

	private static boolean restricted(JsonNode e, String field) {
		return RESTRICTED.equals(text(e, field));
	}

	private static String text(JsonNode e, String field) {
		JsonNode v = e.path(field);
		if (v.isMissingNode() || v.isNull()) {
			return null;
		}
		String s = v.asString().strip();
		return s.isEmpty() ? null : s;
	}

	private static Integer integer(JsonNode e, String field) {
		JsonNode v = e.path(field);
		return v.isNumber() ? v.intValue() : null;
	}

	private static BigDecimal decimal(JsonNode e, String field) {
		JsonNode v = e.path(field);
		return v.isNumber() ? v.decimalValue() : null;
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(v -> {
			if (v.isString() && !v.asString().isBlank()) {
				values.add(v.asString().strip());
			}
		});
		return values;
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

}
