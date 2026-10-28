package com.youthbenefit.profile;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 프로필. 비어 있는 칸은 "아직 모름"이고, 판정에서 "확인 필요"가 된다. */
@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID anonymousId;

	private LocalDate birthDate;
	private String zipCd;
	private String actualZipCd;
	private String jobCd;
	private String schoolCd;
	private String mrgSttsCd;
	private String plcyMajorCd;

	/** 콤마 목록. null = 입력 안 함, "" = 해당 없음 */
	private String sbizCd;

	private Integer annualIncome;
	private Integer householdMedianIncomePct;
	private Integer householdIncomeYear;

	@Enumerated(EnumType.STRING)
	private HousingType housingType;

	private Boolean homeowner;
	private String interestCategories;

	/** 학교 장학 판정용 (조건 JSON 명세의 학교용 변수) */
	private String schoolName;

	@Enumerated(EnumType.STRING)
	private EnrollmentStatus enrollmentStatus;

	private Integer gradeLevel;
	private BigDecimal gpa;
	private BigDecimal gpaScale;

	/** 한국장학재단 학자금 지원구간 0~10 (0 = 기초·차상위) */
	private Integer kosafIncomeBracket;

	/** 사용자가 쓴 전공 이름. 계열 코드(plcyMajorCd)는 이것에서 정한다(코드를 직접 보내면 그 코드) */
	private String majorName;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public static UserProfile create(UUID anonymousId) {
		UserProfile profile = new UserProfile();
		profile.anonymousId = anonymousId;
		return profile;
	}

	/** 화면에서 보낸 값으로 전부 바꾼다(PUT). 보내지 않은 항목은 "모름"으로 비워진다. */
	public void replace(ProfileRequest r) {
		birthDate = r.birthDate();
		zipCd = r.zipCd();
		actualZipCd = r.actualZipCd();
		jobCd = r.jobCd();
		schoolCd = r.schoolCd();
		mrgSttsCd = r.mrgSttsCd();
		majorName = r.majorName() == null || r.majorName().isBlank() ? null : r.majorName().strip();
		// 전공 이름만 왔으면 계열을 이름에서 정한다 (디자인 01: 전공을 글로 입력)
		plcyMajorCd = r.plcyMajorCd() != null ? r.plcyMajorCd() : MajorCategories.codeOf(majorName);
		sbizCd = r.sbizCd() == null ? null : String.join(",", r.sbizCd());
		annualIncome = r.annualIncome();
		householdMedianIncomePct = r.householdMedianIncomePct();
		householdIncomeYear = r.householdIncomeYear();
		housingType = r.housingType();
		homeowner = r.homeowner();
		interestCategories = r.interestCategories() == null ? null : String.join(",", r.interestCategories());
		schoolName = r.schoolName() == null || r.schoolName().isBlank() ? null : r.schoolName().strip();
		enrollmentStatus = r.enrollmentStatus();
		gradeLevel = r.gradeLevel();
		gpa = r.gpa();
		gpaScale = r.gpaScale();
		kosafIncomeBracket = r.kosafIncomeBracket();
	}

	/** 지금 저장된 값을 요청 모양으로. PATCH 에서 보낸 항목만 덮어쓸 때 쓴다. */
	public ProfileRequest toRequest() {
		return new ProfileRequest(birthDate, zipCd, actualZipCd, jobCd, schoolCd, mrgSttsCd, plcyMajorCd, list(sbizCd),
				annualIncome, householdMedianIncomePct, householdIncomeYear, housingType, homeowner,
				list(interestCategories), schoolName, enrollmentStatus, gradeLevel, gpa, gpaScale, kosafIncomeBracket,
				majorName);
	}

	private static List<String> list(String commaSeparated) {
		if (commaSeparated == null) {
			return null;
		}
		return commaSeparated.isEmpty() ? List.of() : Arrays.asList(commaSeparated.split(","));
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

}
