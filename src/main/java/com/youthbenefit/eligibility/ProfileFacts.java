package com.youthbenefit.eligibility;

import com.youthbenefit.profile.UserProfile;
import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 판정에 쓰는 사용자 값. 변수 이름은 조건 JSON 명세 v0.1 과 같다. 값이 없으면(null) "모름"이다.
 * sbizCd 는 목록이고, 빈 목록은 "해당 없음"이다.
 */
public final class ProfileFacts {

	private final Map<String, Object> values;

	private ProfileFacts(Map<String, Object> values) {
		this.values = values;
	}

	public static ProfileFacts empty() {
		return new ProfileFacts(Map.of());
	}

	/** 나이는 신청일(오늘) 기준 만 나이. */
	public static ProfileFacts of(UserProfile p, LocalDate today) {
		Map<String, Object> v = new HashMap<>();
		if (p.getBirthDate() != null) {
			v.put("age", Period.between(p.getBirthDate(), today).getYears());
		}
		put(v, "zipCd", p.getZipCd());
		put(v, "jobCd", p.getJobCd());
		put(v, "schoolCd", p.getSchoolCd());
		put(v, "mrgSttsCd", p.getMrgSttsCd());
		put(v, "plcyMajorCd", p.getPlcyMajorCd());
		if (p.getSbizCd() != null) {
			v.put("sbizCd", p.getSbizCd().isEmpty() ? List.of() : Arrays.asList(p.getSbizCd().split(",")));
		}
		put(v, "annualIncome", p.getAnnualIncome());
		put(v, "householdMedianIncomePct", p.getHouseholdMedianIncomePct());
		put(v, "housingType", p.getHousingType() == null ? null : p.getHousingType().name());
		put(v, "homeowner", p.getHomeowner() == null ? null : p.getHomeowner().toString());
		return new ProfileFacts(v);
	}

	private static void put(Map<String, Object> v, String key, Object value) {
		if (value != null) {
			v.put(key, value);
		}
	}

	Object get(String var) {
		return values.get(var);
	}

}
