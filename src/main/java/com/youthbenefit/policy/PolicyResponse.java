package com.youthbenefit.policy;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** 공고 상세 응답. 자격 조건은 온통청년 코드 그대로, 지역·분류는 화면용 값을 함께 내려준다. */
public record PolicyResponse(
		Long id,
		PolicySource source,
		String title,
		String description,
		String supportContent,
		List<String> categories,
		String organization,
		String applyPeriodCode,
		LocalDate applyStartDate,
		LocalDate applyEndDate,
		boolean nationwide,
		List<String> regions,
		Integer sprtTrgtMinAge,
		Integer sprtTrgtMaxAge,
		String zipCd,
		String earnCndSeCd,
		Integer earnMaxAmt,
		String jobCd,
		String schoolCd,
		String mrgSttsCd,
		String plcyMajorCd,
		String sbizCd,
		String applyUrl,
		String referenceUrl1,
		String submissionDocuments) {

	public static PolicyResponse from(Policy p) {
		return new PolicyResponse(p.getId(), p.getSource(), p.getTitle(), p.getDescription(),
				p.getSupportContent(), split(p.getCategoryGroup()), p.getOrganization(), p.getApplyPeriodCode(),
				p.getApplyStartDate(), p.getApplyEndDate(), p.isNationwide(), regions(p),
				p.getSprtTrgtMinAge(), p.getSprtTrgtMaxAge(), p.getZipCd(), p.getEarnCndSeCd(), p.getEarnMaxAmt(),
				p.getJobCd(), p.getSchoolCd(), p.getMrgSttsCd(), p.getPlcyMajorCd(), p.getSbizCd(), p.getApplyUrl(),
				p.getReferenceUrl1(), p.getSubmissionDocuments());
	}

	static List<String> regions(Policy p) {
		return Regions.displayNames(p.isNationwide(), split(p.getSidoCodes()));
	}

	static List<String> split(String commaSeparated) {
		if (commaSeparated == null || commaSeparated.isBlank()) {
			return List.of();
		}
		return Arrays.stream(commaSeparated.split(",")).map(String::strip).toList();
	}

}
