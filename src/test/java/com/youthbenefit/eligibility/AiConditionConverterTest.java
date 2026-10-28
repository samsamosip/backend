package com.youthbenefit.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.youthbenefit.eligibility.AiConditionConverter.Source;
import com.youthbenefit.eligibility.AiConditions.Requirement;
import com.youthbenefit.eligibility.Condition.Cmp;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실데이터로 변환해 보며 나온 AI 실수들을 검사 규칙이 막는지 (공고 번호는 로컬 실측). */
class AiConditionConverterTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void promptBuildsWithoutFormatErrors() {
		assertThat(AiConditionConverter.SYSTEM).contains("0049005 대학 재학").contains("기준중위소득 %").doesNotContain("{SCHOOL}");
	}

	@Test
	void acceptsGroundedRules() {
		AiConditions c = parse(add("19 ~ 39세 대구시 거주 무주택 청년 - (소득기준) 본인 연소득 6천만원 이하"),
				rule("age", "BETWEEN", "19 ~ 39세", "19", "39"), rule("region", "IN", "대구시 거주", "대구"),
				rule("homeowner", "IN", "무주택 청년", "false"), rule("annualIncome", "LTE", "본인 연소득 6천만원 이하", "6000"));
		assertThat(c.requirements()).extracting(Requirement::var)
			.containsExactly("age", "zipCd", "homeowner", "annualIncome");
		assertThat(c.requirements().get(1).cmp()).isEqualTo(Cmp.STARTS_WITH);
		assertThat(c.requirements().get(1).values()).containsExactly("27");
		assertThat(c.unresolved()).isEmpty();
	}

	@Test
	void medianIncomeRangeBecomesTwoChecks() {
		AiConditions c = parse(income("기준중위소득 60 ~ 160%이하"),
				rule("householdMedianIncomePct", "BETWEEN", "기준중위소득 60 ~ 160%이하", "60", "160"));
		assertThat(c.requirements()).extracting(Requirement::cmp).containsExactly(Cmp.GTE, Cmp.LTE);
	}

	@Test
	void rejectsRevenueAndMedianIncomeReadAsAnnualIncome() {
		// #4 "연 매출 3억원 이하" → 연소득, #158 "기준 중위소득 180% 이하" → 연소득 180만원
		assertRejected(parse(add("연 매출 3억원 이하"), rule("annualIncome", "LTE", "연 매출 3억원 이하", "30000")));
		assertRejected(parse(income("기준 중위소득 180% 이하"), rule("annualIncome", "LTE", "기준 중위소득 180% 이하", "180")));
	}

	@Test
	void rejectsChildAgeAsApplicantAge() {
		// #171 "4세 ~ 12세(초등학생) 아동", #170 "생후 24개월 이하 영아"
		assertRejected(parse(add("북구에 주소를 둔 4세 ~ 12세(초등학생) 아동"),
				rule("age", "BETWEEN", "4세 ~ 12세(초등학생) 아동", "4", "12")));
		assertRejected(parse(add("생후 24개월 이하 영아동반 가정"), rule("age", "LTE", "생후 24개월 이하 영아동반", "2")));
	}

	@Test
	void rejectsEvidenceNotInTextAndNumbersNotInEvidence() {
		assertRejected(parse(add("서울 거주 청년"), rule("homeowner", "IN", "무주택자", "false")));
		// #33 근거는 "150% 이하"인데 60% 이상을 지어냄
		assertRejected(parse(income("중위소득 150% 이하"),
				rule("householdMedianIncomePct", "BETWEEN", "중위소득 150% 이하", "60", "150")));
	}

	@Test
	void rejectsAlternativesAndFuturePlans() {
		// #1042 "인천 거주 또는 사업자 등록", #821 "화순군에 전입하고자", #1962 "석사 이상 혹은 준조사원"
		assertRejected(parse(add("인천 거주 또는 사업자 등록한 예비 청년창업가"), rule("region", "IN", "인천 거주", "인천")));
		assertRejected(parse(add("18세 ~ 49세인 화순군에 전입하고자하는 청년"), rule("region", "IN", "화순군에 전입하고자하는", "전남 화순군")));
		assertRejected(parse(add("전문인력 자격은 석사 이상 혹은 준조사원"), rule("schoolCd", "IN", "석사 이상", "0049008")));
	}

	@Test
	void rejectsConditionsForOnlySomeApplicants() {
		// #1380, #393, #688
		assertRejected(parse(income("2019. 1. 1. 혼인 혹은 자녀출산한 가구 사회초년생은 신청일 기준 만 19세 ~ 39세 이하로 취업후 5년 이내"),
				rule("age", "BETWEEN", "만 19세 ~ 39세 이하", "19", "39")));
		assertRejected(parse(income("(소득 기준) 중위소득 100% (단, 가족돌봄청년 자기돌봄비 한함)"),
				rule("householdMedianIncomePct", "LTE", "중위소득 100%", "100")));
		assertRejected(parse(income("기준 중위소득 150% 이하(1인 기준 20%p 추가 가산)"),
				rule("householdMedianIncomePct", "LTE", "기준 중위소득 150% 이하", "150")));
	}

	@Test
	void rejectsInvertedRegionAndSchoolLocation() {
		// #149 "대구 외 타지역 거주 청년", #372 "충북 도내 학교", #407 "도내 청소년"
		assertRejected(parse(add("대구 외 타지역 거주 청년"), rule("region", "IN", "타지역 거주 청년", "대구")));
		assertRejected(parse(add("충북 도내 학교 및 청소년 기관"), rule("region", "IN", "충북 도내 학교", "충북")));
		assertRejected(parse(add("도내 거주 청소년 및 대학생"), rule("region", "IN", "도내 거주 청소년", "충남")));
	}

	@Test
	void codesNeedTheirWordInEvidence() {
		// #473 "미취업자"를 미혼으로
		assertRejected(parse(add("졸업 후 5년 이내 미취업자"), rule("mrgSttsCd", "IN", "졸업 후 5년 이내 미취업자", "0055002")));
		AiConditions married = parse(add("혼인신고 한 부부"), rule("mrgSttsCd", "IN", "혼인신고 한 부부", "0055001"));
		assertThat(married.requirements()).hasSize(1);
	}

	@Test
	void exclusionTextOnlyGivesHomeowner() {
		// #219 "재학생 참여 불가" → 재학생만?  /  "주택소유자 제외" → 무주택
		assertRejected(parse(exclusion("재학생 참여 불가"), rule("schoolCd", "IN", "재학생 참여 불가", "0049005")));
		AiConditions c = parse(exclusion("주택소유자(부모 명의 주택 거주자 포함)"), rule("homeowner", "IN", "주택소유자", "false"));
		assertThat(c.requirements()).extracting(Requirement::var).containsExactly("homeowner");
	}

	@Test
	void severalThresholdsOnlyAllowTheLoosest() {
		// #944 국민취업지원제도: Ⅰ유형 60%, 선발형 120%, Ⅱ유형 청년은 소득 무관
		Source employment = add("Ⅰ유형 중위소득 60% 이하 / 선발형 중위소득 120% 이하 / Ⅱ유형 중위소득 100% 이하");
		assertRejected(parse(employment, rule("householdMedianIncomePct", "LTE", "Ⅰ유형 중위소득 60% 이하", "60")));
		assertThat(parse(employment, rule("householdMedianIncomePct", "LTE", "선발형 중위소득 120% 이하", "120")).requirements())
			.hasSize(1);
		assertRejected(parse(income("Ⅰ유형 중위소득 60% 이하 / Ⅱ유형 소득 무관"),
				rule("householdMedianIncomePct", "LTE", "Ⅰ유형 중위소득 60% 이하", "60")));
		// 대상마다 연소득 기준이 다름
		assertRejected(parse(add("연소득 (청년) 5천만원, (신혼부부) 7.5천만원 이하"),
				rule("annualIncome", "LTE", "연소득 (청년) 5천만원", "5000")));
	}

	@Test
	void schoolScholarshipRules() {
		// #3593 은평구민 장학생
		AiConditions c = parse(add("2026년 1학기 평균 평점 4.5만점 기준 3.0 이상 2026년 2학기 학자금 지원 소득구간 8구간 이내"),
				rule("gpa", "GTE", "2026년 1학기 평균 평점 4.5만점 기준 3.0 이상", "3.0", "4.5"),
				rule("kosafIncomeBracket", "LTE", "학자금 지원 소득구간 8구간 이내", "8"));
		assertThat(c.requirements()).extracting(Requirement::var).containsExactly("gpa", "kosafIncomeBracket");
		// 만점이 이상하면 버림, 이수 학점은 평점이 아님
		assertRejected(parse(add("평점 3.0 이상(10점 만점)"), rule("gpa", "GTE", "평점 3.0 이상(10점 만점)", "3.0", "10")));
		assertRejected(parse(add("직전 학기 12학점 이상 이수"), rule("gpa", "GTE", "직전 학기 12학점 이상 이수", "12", "4.5")));
		// 앞으로의 학년은 지금 학년이 아님
		assertRejected(parse(add("27년 3월 기준 2-1학기, 3-1학기 진학 예정인 자"),
				rule("gradeLevel", "BETWEEN", "2-1학기, 3-1학기 진학 예정인 자", "2", "3")));
		assertThat(parse(add("전국 4년제 대학 2~3학년 지원 가능"), rule("gradeLevel", "BETWEEN", "4년제 대학 2~3학년", "2", "3"))
			.requirements()).hasSize(1);
	}

	@Test
	void underAndOverAreStrict() {
		// #3585 "만 30세 미만" → 29세 이하
		AiConditions c = parse(add("만 30세 미만 (1996년 10월 15일 이후 출생자)"), rule("age", "LTE", "만 30세 미만", "30"));
		assertThat(c.requirements().get(0).values()).containsExactly("29");
		AiConditions over = parse(add("학자금 지원구간 3구간 초과"), rule("kosafIncomeBracket", "GTE", "학자금 지원구간 3구간 초과", "3"));
		assertThat(over.requirements().get(0).values()).containsExactly("4");
	}

	@Test
	void noConditionsOnlyForTrivialText() {
		assertThat(parse(add("해당 없음"), none()).noExtraConditions()).isTrue();
		assertThat(parse(new Source("무관, 소득별 본인부담금 차등", "-", "해당없음"), none()).noExtraConditions()).isTrue();
		// #534 "트랙별 상이", #657 "저소득 청년 대상" — AI 가 조건 없다고 해도 원문을 확인 필요로 남긴다
		AiConditions track = parse(add("대회 트랙별 상이하므로 해당 대회 공고 내용 확인"), none());
		assertThat(track.noExtraConditions()).isFalse();
		assertThat(track.unresolved()).extracting(AiConditions.Unresolved::label).containsExactly("추가 자격 조건");
		assertThat(parse(income("차상위계층 이하 등 저소득 청년 대상"), none()).unresolved()).hasSize(1);
	}

	@Test
	void rejectedRuleStaysAsNeedsCheckAndAsksForReview() {
		JsonNode raw = raw(List.of(rule("annualIncome", "LTE", "연 매출 3억원 이하", "30000")));
		AiConditionConverter.Parsed parsed = AiConditionConverter.parse(raw, "m", add("연 매출 3억원 이하"));
		assertThat(parsed.needsReview()).isTrue();
		assertThat(parsed.conditions().unresolved()).hasSize(1);
	}

	// ── 도우미 ───────────────────────────────────────────

	private static void assertRejected(AiConditions c) {
		assertThat(c.requirements()).isEmpty();
		assertThat(c.unresolved()).isNotEmpty();
	}

	private static Source add(String text) {
		return new Source(null, text, null);
	}

	private static Source income(String text) {
		return new Source(text, null, null);
	}

	private static Source exclusion(String text) {
		return new Source(null, null, text);
	}

	private static String rule(String var, String op, String evidence, String... values) {
		return JSON.writeValueAsString(java.util.Map.of("var", var, "op", op, "values", List.of(values), "evidence", evidence));
	}

	private static String none() {
		return null;
	}

	private static AiConditions parse(Source source, String... rules) {
		List<String> list = rules.length == 1 && rules[0] == null ? List.of() : List.of(rules);
		return AiConditionConverter.parse(raw(list), "m", source).conditions();
	}

	private static JsonNode raw(List<String> rules) {
		return JSON.readTree("{\"noExtraConditions\":" + rules.isEmpty() + ",\"requirements\":[" + String.join(",", rules)
				+ "],\"unresolved\":[],\"confidence\":\"high\"}");
	}

}
