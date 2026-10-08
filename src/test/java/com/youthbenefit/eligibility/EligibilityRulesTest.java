package com.youthbenefit.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.youthbenefit.ontong.OntongPolicyMapper;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicySource;
import com.youthbenefit.profile.HousingType;
import com.youthbenefit.profile.ProfileRequest;
import com.youthbenefit.profile.UserProfile;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EligibilityRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

	private static final String ALL_SIDO = "11110,26110,27110,28110,30110,31110,36110,41111,51110,43111,44131,52111,12110,47111,48121,50110";

	private final OntongPolicyMapper mapper = new OntongPolicyMapper(JsonMapper.builder().build());

	private final EligibilityService service = new EligibilityService(null, null);

	/** 제한 없는 온통청년 공고에서 주어진 칸만 바꾼다. */
	private Policy policy(String... overrides) {
		Map<String, Object> item = new HashMap<>(Map.of("plcyNo", "P1", "plcyNm", "테스트 공고", "zipCd", ALL_SIDO,
				"sprtTrgtMinAge", "0", "sprtTrgtMaxAge", "0", "earnCndSeCd", "0043001", "jobCd", "0013010", "schoolCd",
				"0049010", "mrgSttsCd", "0055003", "plcyMajorCd", "0011009"));
		item.put("sbizCd", "0014010");
		for (int i = 0; i < overrides.length; i += 2) {
			item.put(overrides[i], overrides[i + 1]);
		}
		return Policy.create(PolicySource.ONTONG, "P1", mapper.toContent(item));
	}

	/** 2003-06-12생, 관악구, 미취업, 대학 재학, 월세. 소득·특화 대상은 모름. */
	private static ProfileRequest jimin(Integer income, List<String> sbiz, LocalDate birth) {
		return new ProfileRequest(birth, "11620", null, "0013003", "0049005", null, null, sbiz, income, null, null,
				HousingType.MONTHLY_RENT, null, null);
	}

	private PolicyEligibility judge(Policy policy, ProfileRequest request) {
		UserProfile profile = UserProfile.create(UUID.randomUUID());
		profile.replace(request);
		return service.judge(policy, ProfileFacts.of(profile, TODAY));
	}

	private PolicyEligibility judge(Policy policy) {
		return judge(policy, jimin(null, null, LocalDate.of(2003, 6, 12)));
	}

	@Test
	void threeValuedLogic() {
		assertThat(Truth.and(List.of(Truth.TRUE, Truth.UNKNOWN))).isEqualTo(Truth.UNKNOWN);
		assertThat(Truth.and(List.of(Truth.UNKNOWN, Truth.FALSE))).isEqualTo(Truth.FALSE);
		assertThat(Truth.and(List.of())).isEqualTo(Truth.TRUE);
		assertThat(Truth.or(List.of(Truth.FALSE, Truth.UNKNOWN))).isEqualTo(Truth.UNKNOWN);
		assertThat(Truth.or(List.of(Truth.UNKNOWN, Truth.TRUE))).isEqualTo(Truth.TRUE);
		assertThat(Truth.UNKNOWN.not()).isEqualTo(Truth.UNKNOWN);
	}

	@Test
	void unrestrictedPolicyMatchesEveryone() {
		PolicyEligibility e = judge(policy());

		assertThat(e.verdict()).isEqualTo(Verdict.MATCH);
		assertThat(e.conditions()).isEmpty();
		assertThat(e.summary()).isEqualTo("자격 제한이 없는 공고예요");
	}

	@Test
	void allStatedConditionsMatch() {
		PolicyEligibility e = judge(policy("sprtTrgtMinAge", "19", "sprtTrgtMaxAge", "34", "zipCd", "11620,11680",
				"jobCd", "0013003,0013006", "schoolCd", "0049005"));

		assertThat(e.verdict()).isEqualTo(Verdict.MATCH);
		assertThat(e.conditions()).extracting(ConditionResult::label).containsExactly("나이", "지역", "취업 상태", "학력");
		assertThat(e.conditions().getFirst().requirement()).isEqualTo("만 19~34세");
		assertThat(e.conditions().getFirst().myValue()).isEqualTo("만 23세");
		assertThat(e.conditions().get(2).requirement()).isEqualTo("미취업자, (예비)창업자");
	}

	@Test
	void missingIncomeNeedsCheckNotGuess() {
		// 기획안 9쪽: 소득 미입력 → 확인 필요
		PolicyEligibility e = judge(policy("earnCndSeCd", "0043002", "earnMaxAmt", "5000"));

		assertThat(e.verdict()).isEqualTo(Verdict.NEEDS_CHECK);
		assertThat(e.missingFields()).containsExactly("annualIncome");
		assertThat(e.summary()).isEqualTo("소득 확인 필요");
		assertThat(e.conditions().getFirst().requirement()).isEqualTo("연소득 5,000만원 이하");

		assertThat(judge(policy("earnCndSeCd", "0043002", "earnMaxAmt", "5000"),
				jimin(3000, null, LocalDate.of(2003, 6, 12))).verdict()).isEqualTo(Verdict.MATCH);
		assertThat(judge(policy("earnCndSeCd", "0043002", "earnMaxAmt", "5000"),
				jimin(6000, null, LocalDate.of(2003, 6, 12))).verdict()).isEqualTo(Verdict.NO_MATCH);
	}

	@Test
	void definiteMismatchWinsOverUnknown() {
		// 강원 공고: 소득은 모르지만 지역이 확정 불일치 → 불일치
		PolicyEligibility e = judge(policy("zipCd", "51820", "earnCndSeCd", "0043002", "earnMaxAmt", "5000"));

		assertThat(e.verdict()).isEqualTo(Verdict.NO_MATCH);
		assertThat(e.summary()).isEqualTo("지역 조건이 맞지 않아요");
	}

	@Test
	void ageBoundaryFollowsBirthday() {
		// 만 34세까지인 공고: 2026-10-09 기준 1991-10-09생은 35세, 1991-10-10생은 34세
		Policy upTo34 = policy("sprtTrgtMinAge", "19", "sprtTrgtMaxAge", "34");

		assertThat(judge(upTo34, jimin(null, null, LocalDate.of(1991, 10, 10))).verdict()).isEqualTo(Verdict.MATCH);
		assertThat(judge(upTo34, jimin(null, null, LocalDate.of(1991, 10, 9))).verdict()).isEqualTo(Verdict.NO_MATCH);
	}

	@Test
	void hugeMaxAgeMeansNoUpperLimit() {
		// 온통청년은 상한 없음을 99·100·120·999 로 적기도 한다
		for (String max : List.of("99", "120", "999")) {
			PolicyEligibility e = judge(policy("sprtTrgtMinAge", "19", "sprtTrgtMaxAge", max));
			assertThat(e.conditions().getFirst().requirement()).isEqualTo("만 19세 이상");
			assertThat(e.verdict()).isEqualTo(Verdict.MATCH);
		}
		assertThat(judge(policy("sprtTrgtMinAge", "0", "sprtTrgtMaxAge", "999")).conditions()).isEmpty();
		assertThat(judge(policy("sprtTrgtMinAge", "19", "sprtTrgtMaxAge", "80")).conditions().getFirst().requirement())
			.isEqualTo("만 19~80세");
	}

	@Test
	void specialGroupUnknownVersusNone() {
		Policy forWomen = policy("sbizCd", "0014002");

		assertThat(judge(forWomen, jimin(null, null, LocalDate.of(2003, 6, 12))).verdict())
			.isEqualTo(Verdict.NEEDS_CHECK);
		assertThat(judge(forWomen, jimin(null, List.of(), LocalDate.of(2003, 6, 12))).verdict())
			.isEqualTo(Verdict.NO_MATCH);
		assertThat(judge(forWomen, jimin(null, List.of("0014002"), LocalDate.of(2003, 6, 12))).verdict())
			.isEqualTo(Verdict.MATCH);
	}

	@Test
	void textOnlyConditionsStayUnknown() {
		// 해석하지 못한 필수 조건이 남으면 조건 일치로 단정하지 않는다
		PolicyEligibility e = judge(policy("earnCndSeCd", "0043003", "earnEtcCn", "가구 기준중위소득 60% 이하",
				"addAplyQlfcCndCn", "무주택자", "ptcpPrpTrgtCn", "기존 수혜자 제외"));

		assertThat(e.verdict()).isEqualTo(Verdict.NEEDS_CHECK);
		assertThat(e.summary()).isEqualTo("소득 조건·추가 자격 조건·참여 제한 대상 확인 필요");
		assertThat(e.conditions()).extracting(ConditionResult::evidence)
			.containsExactly("가구 기준중위소득 60% 이하", "무주택자", "기존 수혜자 제외");
		assertThat(e.conditions().getLast().requirement()).startsWith("해당하면 제외");
	}

	@Test
	void emptyProfileMakesConditionsUnknown() {
		PolicyEligibility e = service.judge(policy("sprtTrgtMinAge", "19", "sprtTrgtMaxAge", "34", "zipCd", "11620"),
				ProfileFacts.empty());

		assertThat(e.verdict()).isEqualTo(Verdict.NEEDS_CHECK);
		assertThat(e.missingFields()).containsExactly("age", "zipCd");
	}

}
