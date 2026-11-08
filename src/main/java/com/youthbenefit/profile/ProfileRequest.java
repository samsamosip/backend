package com.youthbenefit.profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

/**
 * 프로필 저장 요청. 모든 항목은 선택이다 — 모르면 보내지 않거나 null.
 * sbizCd 는 null(모름)과 [](해당 없음)을 구분한다. 코드값은 GET /api/v1/codes 의 code 를 쓴다.
 * schoolName 부터는 학교 장학 판정용이다. gpa 와 gpaScale 은 함께 보낸다.
 */
public record ProfileRequest(
		@Past(message = "오늘 이전 날짜여야 합니다") LocalDate birthDate,
		@Pattern(regexp = "\\d{5}", message = "시군구 코드 5자리여야 합니다") String zipCd,
		@Pattern(regexp = "\\d{5}", message = "시군구 코드 5자리여야 합니다") String actualZipCd,
		String jobCd,
		String schoolCd,
		String mrgSttsCd,
		String plcyMajorCd,
		List<String> sbizCd,
		@Min(value = 0, message = "0 이상이어야 합니다") Integer annualIncome,
		@Min(value = 0, message = "0~1000 사이여야 합니다") @Max(value = 1000, message = "0~1000 사이여야 합니다") Integer householdMedianIncomePct,
		@Min(value = 2000, message = "2000~2100 사이여야 합니다") @Max(value = 2100, message = "2000~2100 사이여야 합니다") Integer householdIncomeYear,
		HousingType housingType,
		Boolean homeowner,
		List<String> interestCategories,
		@Size(max = 100, message = "100자 이하여야 합니다") String schoolName,
		EnrollmentStatus enrollmentStatus,
		@Min(value = 1, message = "1~6 사이여야 합니다") @Max(value = 6, message = "1~6 사이여야 합니다") Integer gradeLevel,
		@DecimalMin(value = "0", message = "0 이상이어야 합니다") BigDecimal gpa,
		BigDecimal gpaScale,
		@Min(value = 0, message = "0~10 사이여야 합니다") @Max(value = 10, message = "0~10 사이여야 합니다") Integer kosafIncomeBracket,
		@Size(max = 100, message = "100자 이하여야 합니다") String majorName) {

	/** 전공 이름 없이 (예전 모양) */
	public ProfileRequest(LocalDate birthDate, String zipCd, String actualZipCd, String jobCd, String schoolCd,
			String mrgSttsCd, String plcyMajorCd, List<String> sbizCd, Integer annualIncome,
			Integer householdMedianIncomePct, Integer householdIncomeYear, HousingType housingType, Boolean homeowner,
			List<String> interestCategories, String schoolName, EnrollmentStatus enrollmentStatus, Integer gradeLevel,
			BigDecimal gpa, BigDecimal gpaScale, Integer kosafIncomeBracket) {
		this(birthDate, zipCd, actualZipCd, jobCd, schoolCd, mrgSttsCd, plcyMajorCd, sbizCd, annualIncome,
				householdMedianIncomePct, householdIncomeYear, housingType, homeowner, interestCategories, schoolName,
				enrollmentStatus, gradeLevel, gpa, gpaScale, kosafIncomeBracket, null);
	}

}
