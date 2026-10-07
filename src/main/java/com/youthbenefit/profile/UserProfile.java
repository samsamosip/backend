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
import java.time.LocalDate;
import java.time.LocalDateTime;
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
		plcyMajorCd = r.plcyMajorCd();
		sbizCd = r.sbizCd() == null ? null : String.join(",", r.sbizCd());
		annualIncome = r.annualIncome();
		householdMedianIncomePct = r.householdMedianIncomePct();
		householdIncomeYear = r.householdIncomeYear();
		housingType = r.housingType();
		homeowner = r.homeowner();
		interestCategories = r.interestCategories() == null ? null : String.join(",", r.interestCategories());
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
