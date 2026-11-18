package com.youthbenefit.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MedianIncomeTextTest {

	@Test
	void readsASingleMedianIncomeLimit() {
		for (String text : new String[] { "중위소득 150% 이하", "기준 중위소득 65% 이하", "기준중위소득 48% 이하", "중위소득150%이하",
				"청년가구 중위소득 150% 이하", "기준 중위소득 120% 이하 가구", "2026년 건강보험료 기준 중위소득 140% 이하",
				"가구당 건강보험료 부과액 기준 중위소득 150% 이하", "(소득)건강보험료 부과액 기준 중위소득 150% 이하" }) {
			Condition.Check c = MedianIncomeText.read(text);
			assertThat(c).as(text).isNotNull();
			assertThat(c.var()).isEqualTo("householdMedianIncomePct");
			assertThat(c.cmp()).isEqualTo(Condition.Cmp.LTE);
		}
		assertThat(MedianIncomeText.read("기준 중위소득 65% 이하").values()).containsExactly("65");
	}

	@Test
	void leavesMixedOrRangeConditionsAlone() {
		for (String text : new String[] { "소득 : (청년독립가구) 기준 중위소득 60%이하 (원가구) 기준 중위소득 100% 이하",
				"가구 기준 중위소득 50% 초과 ~ 150% 이하인 자", "기준 중위소득 150% 이하(1인 기준 20%p 추가 가산)",
				"4인 가족 기준중위소득 140% 미만", "천안아산 통학 학생 및 기준중위소득 150% 이하", "생계급여(기준 중위소득 32%이하) 수급가구",
				"자기돌봄비 지급은 중위소득 100% 이내인 경우 지원" }) {
			assertThat(MedianIncomeText.read(text)).as(text).isNull();
		}
	}

}
