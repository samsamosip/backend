package com.youthbenefit.profile;

import com.youthbenefit.policy.Regions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 프로필 응답. 저장한 값과 함께 화면용 계산값(만 나이, 시도 이름, "서울 관악구" 같은 지역 이름)과 아직 비어 있는 항목 목록을 준다.
 * missingFields 는 판정에서 "확인 필요"의 원인이 될 수 있는 항목이다. 학교 항목(schoolName~kosafIncomeBracket)은
 * 학생만 답하는 항목이라 넣지 않는다 — 학교 장학 판정에서 필요하면 그 판정의 missingFields 에 나온다.
 */
public record ProfileResponse(
		LocalDate birthDate,
		Integer age,
		String zipCd,
		String residenceSido,
		String residenceName,
		String actualZipCd,
		String jobCd,
		String schoolCd,
		String mrgSttsCd,
		String plcyMajorCd,
		List<String> sbizCd,
		Integer annualIncome,
		Integer householdMedianIncomePct,
		Integer householdIncomeYear,
		HousingType housingType,
		Boolean homeowner,
		List<String> interestCategories,
		String schoolName,
		EnrollmentStatus enrollmentStatus,
		Integer gradeLevel,
		BigDecimal gpa,
		BigDecimal gpaScale,
		Integer kosafIncomeBracket,
		List<String> missingFields,
		LocalDateTime updatedAt) {

	public static ProfileResponse from(UserProfile p, LocalDate today) {
		Integer age = p.getBirthDate() == null ? null : Period.between(p.getBirthDate(), today).getYears();
		String sido = p.getZipCd() == null ? null : Regions.displayNames(false, Regions.sidoCodes(p.getZipCd()))
			.stream()
			.findFirst()
			.orElse(null);
		return new ProfileResponse(p.getBirthDate(), age, p.getZipCd(), sido, Regions.displayName(p.getZipCd()),
				p.getActualZipCd(), p.getJobCd(),
				p.getSchoolCd(), p.getMrgSttsCd(), p.getPlcyMajorCd(), splitOrNull(p.getSbizCd()),
				p.getAnnualIncome(), p.getHouseholdMedianIncomePct(), p.getHouseholdIncomeYear(),
				p.getHousingType(), p.getHomeowner(), splitOrNull(p.getInterestCategories()), p.getSchoolName(),
				p.getEnrollmentStatus(), p.getGradeLevel(), p.getGpa(), p.getGpaScale(), p.getKosafIncomeBracket(), missing(p),
				p.getUpdatedAt());
	}

	private static List<String> missing(UserProfile p) {
		List<String> missing = new ArrayList<>();
		addIfNull(missing, "birthDate", p.getBirthDate());
		addIfNull(missing, "zipCd", p.getZipCd());
		addIfNull(missing, "jobCd", p.getJobCd());
		addIfNull(missing, "schoolCd", p.getSchoolCd());
		addIfNull(missing, "housingType", p.getHousingType());
		addIfNull(missing, "mrgSttsCd", p.getMrgSttsCd());
		addIfNull(missing, "plcyMajorCd", p.getPlcyMajorCd());
		addIfNull(missing, "sbizCd", p.getSbizCd());
		addIfNull(missing, "annualIncome", p.getAnnualIncome());
		addIfNull(missing, "householdMedianIncomePct", p.getHouseholdMedianIncomePct());
		addIfNull(missing, "homeowner", p.getHomeowner());
		return missing;
	}

	private static void addIfNull(List<String> missing, String field, Object value) {
		if (value == null) {
			missing.add(field);
		}
	}

	/** null 은 null(모름), "" 는 빈 목록(해당 없음). */
	private static List<String> splitOrNull(String commaSeparated) {
		if (commaSeparated == null) {
			return null;
		}
		if (commaSeparated.isEmpty()) {
			return List.of();
		}
		return Arrays.asList(commaSeparated.split(","));
	}

}
