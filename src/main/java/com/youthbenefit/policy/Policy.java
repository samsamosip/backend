package com.youthbenefit.policy;

import jakarta.persistence.Column;
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
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 청년 정책 공고 한 건. 온통청년 필드는 이름과 코드값을 그대로 저장한다 (조건 JSON 명세 v0.1).
 */
@Entity
@Table(name = "policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

	/** 온통청년 신청기간 구분 코드: 마감 */
	public static final String APPLY_PERIOD_CLOSED = "0057003";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	private PolicySource source;

	private String sourceId;

	private String title;
	private String description;
	private String supportContent;
	private String category;
	private String middleCategory;
	private String keywords;
	private String organization;

	private String applyPeriodCode;
	private String applyPeriodRaw;
	private LocalDate applyStartDate;
	private LocalDate applyEndDate;

	private Integer sprtTrgtMinAge;
	private Integer sprtTrgtMaxAge;
	private String sprtTrgtAgeLmtYn;
	private String zipCd;
	private String earnCndSeCd;
	private Integer earnMinAmt;
	private Integer earnMaxAmt;
	private String earnEtcCn;
	private String jobCd;
	private String schoolCd;
	private String mrgSttsCd;
	private String plcyMajorCd;
	private String sbizCd;
	private String addAplyQlfcCndCn;
	private String ptcpPrpTrgtCn;

	private String applyUrl;
	private String referenceUrl1;
	private String referenceUrl2;
	private String applyMethod;
	private String screeningMethod;
	private String submissionDocuments;

	/** zipCd 앞 2자리(시도) 콤마 목록. {@link Regions} 참고 */
	private String sidoCodes;

	/** 16개 시도를 모두 포함하면 전국 */
	private boolean nationwide;

	/** 공식 대분류 5개로 묶은 값, 콤마 목록. {@link Categories} 참고 */
	private String categoryGroup;

	/** 제목과 주관기관으로 만든 중복 판별 키. {@link Duplicates} 참고 */
	private String duplicateKey;

	/** 출처의 버전 id (학교 장학: Harvester version_id). 바뀌었을 때만 상세를 다시 받는다 */
	private String sourceVersion;

	/** 출처가 정리해 준 서류 목록 JSON. {@link PolicyContent} 참고 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String checklistDocuments;

	/** 출처에서 사라짐(합쳐짐·비공개·삭제). 목록·맞춤에서 빠진다 */
	private boolean sourceRemoved;

	/** 직접 등록한 공고(source = USER)의 주인. 공식 공고는 null — 주인 말고는 볼 수 없다 */
	private UUID ownerId;

	/** 직접 등록한 공고의 마감 시각 (예: 18:00) */
	private LocalTime applyEndTime;

	/** 직접 등록한 공고의 메모 */
	private String memo;

	private LocalDateTime sourceModifiedAt;
	private LocalDateTime sourceRegisteredAt;
	private String rawHash;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String rawPayload;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public static Policy create(PolicySource source, String sourceId, PolicyContent content) {
		Policy policy = new Policy();
		policy.source = source;
		policy.sourceId = sourceId;
		policy.apply(content);
		return policy;
	}

	/** 원본이 바뀌었을 때만 true를 돌려주고 내용을 갱신한다. */
	public boolean apply(PolicyContent c) {
		if (c.rawHash().equals(rawHash)) {
			sourceVersion = c.sourceVersion();
			return false;
		}
		title = c.title();
		description = c.description();
		supportContent = c.supportContent();
		category = c.category();
		middleCategory = c.middleCategory();
		keywords = c.keywords();
		organization = c.organization();
		applyPeriodCode = c.applyPeriodCode();
		applyPeriodRaw = c.applyPeriodRaw();
		applyStartDate = c.applyStartDate();
		applyEndDate = c.applyEndDate();
		sprtTrgtMinAge = c.sprtTrgtMinAge();
		sprtTrgtMaxAge = c.sprtTrgtMaxAge();
		sprtTrgtAgeLmtYn = c.sprtTrgtAgeLmtYn();
		zipCd = c.zipCd();
		earnCndSeCd = c.earnCndSeCd();
		earnMinAmt = c.earnMinAmt();
		earnMaxAmt = c.earnMaxAmt();
		earnEtcCn = c.earnEtcCn();
		jobCd = c.jobCd();
		schoolCd = c.schoolCd();
		mrgSttsCd = c.mrgSttsCd();
		plcyMajorCd = c.plcyMajorCd();
		sbizCd = c.sbizCd();
		addAplyQlfcCndCn = c.addAplyQlfcCndCn();
		ptcpPrpTrgtCn = c.ptcpPrpTrgtCn();
		applyUrl = c.applyUrl();
		referenceUrl1 = c.referenceUrl1();
		referenceUrl2 = c.referenceUrl2();
		applyMethod = c.applyMethod();
		screeningMethod = c.screeningMethod();
		submissionDocuments = c.submissionDocuments();
		sourceModifiedAt = c.sourceModifiedAt();
		sourceRegisteredAt = c.sourceRegisteredAt();
		rawHash = c.rawHash();
		rawPayload = c.rawPayload();
		sidoCodes = c.sidoCodes();
		nationwide = c.nationwide();
		categoryGroup = c.categoryGroup();
		duplicateKey = c.duplicateKey();
		sourceVersion = c.sourceVersion();
		checklistDocuments = c.checklistDocuments();
		return true;
	}

	/**
	 * 직접 등록 공고 (WF07). 판정할 조건이 없어서 목록·맞춤에는 안 나오고, 준비함·마감 알림에만 쓴다. 서류 목록(documents)은
	 * 학교 장학처럼 정리된 목록으로 둬서 체크리스트가 그대로 만들어진다.
	 */
	public static Policy custom(UUID ownerId, CustomPolicy input) {
		Policy policy = new Policy();
		policy.source = PolicySource.USER;
		policy.sourceId = UUID.randomUUID().toString();
		policy.ownerId = ownerId;
		policy.rawHash = "user";
		policy.rawPayload = "{}";
		policy.edit(input);
		return policy;
	}

	/** 직접 등록 공고 고치기. 보낸 값 전체로 바꾼다. */
	public void edit(CustomPolicy input) {
		title = input.title();
		organization = input.organization();
		applyEndDate = input.applyEndDate();
		applyEndTime = input.applyEndDate() == null ? null : input.applyEndTime();
		applyPeriodCode = input.applyEndDate() == null ? "0057002" : "0057001";
		categoryGroup = input.category();
		category = input.category();
		memo = input.memo();
		applyUrl = input.applyUrl();
		submissionDocuments = input.documentsText();
		checklistDocuments = input.documentsJson();
		sourceRegisteredAt = sourceRegisteredAt == null ? LocalDateTime.now() : sourceRegisteredAt;
	}

	public boolean isCustom() {
		return ownerId != null;
	}

	/** 공식 공고는 누구나, 직접 등록 공고는 주인만 볼 수 있다. */
	public boolean visibleTo(UUID viewer) {
		return ownerId == null || ownerId.equals(viewer);
	}

	/** 출처에서 사라졌다고 표시한다. 이미 표시돼 있으면 false. */
	public boolean markRemoved() {
		if (sourceRemoved) {
			return false;
		}
		sourceRemoved = true;
		return true;
	}

	/** 출처에 다시 나타났다. */
	public void markRestored() {
		sourceRemoved = false;
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
