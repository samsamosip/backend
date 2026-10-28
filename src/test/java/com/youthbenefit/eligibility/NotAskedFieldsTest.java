package com.youthbenefit.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.youthbenefit.eligibility.Condition.All;
import com.youthbenefit.eligibility.Condition.Check;
import com.youthbenefit.eligibility.Condition.Cmp;
import com.youthbenefit.profile.HousingType;
import com.youthbenefit.profile.ProfileRequest;
import com.youthbenefit.profile.UserProfile;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 화면에서 받지 않는 항목(가구 중위소득 %, 주택 소유)은 "입력하세요"로 안내하지 않는다 (10/28 팀 결정: 소득은 본인 연소득만) */
class NotAskedFieldsTest {

	private static final Condition HOUSING = new All(List.of(new Check("householdMedianIncomePct", Cmp.LTE, List.of("150"), "e"),
			new Check("homeowner", Cmp.IN, List.of("false"), "e"), new Check("annualIncome", Cmp.LTE, List.of("5000"), "e")));

	@Test
	void unknownMedianIncomeAndHomeownerPointToTheNotice() {
		ConditionEvaluator.Evaluation e = ConditionEvaluator.evaluate(HOUSING, ProfileFacts.empty());
		assertThat(e.conditions()).extracting(ConditionResult::missingField)
			.containsExactly(null, null, "annualIncome");
		assertThat(EligibilityService.explanation(Verdict.NEEDS_CHECK, e))
			.isEqualTo("소득을 입력하고, 주택 소유는 공고 원문에서 확인해 주세요");
	}

	@Test
	void ownHousingMeansHomeowner() {
		UserProfile owner = profile(HousingType.OWN);
		UserProfile renter = profile(HousingType.MONTHLY_RENT);
		ConditionEvaluator.Evaluation own = ConditionEvaluator.evaluate(HOUSING, ProfileFacts.of(owner, LocalDate.now()));
		ConditionEvaluator.Evaluation rent = ConditionEvaluator.evaluate(HOUSING, ProfileFacts.of(renter, LocalDate.now()));
		assertThat(Verdict.of(own.truth())).isEqualTo(Verdict.NO_MATCH);
		assertThat(Verdict.of(rent.truth())).isEqualTo(Verdict.NEEDS_CHECK);
	}

	private static UserProfile profile(HousingType housing) {
		UserProfile p = UserProfile.create(UUID.randomUUID());
		p.replace(new ProfileRequest(null, null, null, null, null, null, null, null, 3000, null, null, housing, null, null, null,
				null, null, null, null, null));
		return p;
	}

}
