package com.youthbenefit.policy;

import java.time.LocalDate;
import java.util.List;

/**
 * 목록용 응답. 전국 정책의 zipCd(시군구 코드 250여 개)는 목록에 필요 없어서 빼고, 지역은 "전국" 또는 시도 이름으로 준다.
 */
public record PolicySummaryResponse(
		Long id,
		String title,
		String description,
		List<String> categories,
		String organization,
		String applyPeriodCode,
		LocalDate applyStartDate,
		LocalDate applyEndDate,
		boolean nationwide,
		List<String> regions) {

	public static PolicySummaryResponse from(Policy p) {
		return new PolicySummaryResponse(p.getId(), p.getTitle(), p.getDescription(),
				PolicyResponse.split(p.getCategoryGroup()), p.getOrganization(), p.getApplyPeriodCode(),
				p.getApplyStartDate(), p.getApplyEndDate(), p.isNationwide(), PolicyResponse.regions(p));
	}

}
