package com.youthbenefit.policy;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 출처별 수집기가 만들어 넘기는 공고 내용. {@link Policy#apply}로 엔티티에 반영한다.
 * checklistDocuments 는 출처가 서류 목록을 정리해 준 경우의 JSON([{"name", "optional"}])이고, 없으면 submissionDocuments 글을 쪼갠다.
 */
public record PolicyContent(
		String title,
		String description,
		String supportContent,
		String category,
		String middleCategory,
		String keywords,
		String organization,
		String applyPeriodCode,
		String applyPeriodRaw,
		LocalDate applyStartDate,
		LocalDate applyEndDate,
		Integer sprtTrgtMinAge,
		Integer sprtTrgtMaxAge,
		String sprtTrgtAgeLmtYn,
		String zipCd,
		String earnCndSeCd,
		Integer earnMinAmt,
		Integer earnMaxAmt,
		String earnEtcCn,
		String jobCd,
		String schoolCd,
		String mrgSttsCd,
		String plcyMajorCd,
		String sbizCd,
		String addAplyQlfcCndCn,
		String ptcpPrpTrgtCn,
		String applyUrl,
		String referenceUrl1,
		String referenceUrl2,
		String applyMethod,
		String screeningMethod,
		String submissionDocuments,
		LocalDateTime sourceModifiedAt,
		LocalDateTime sourceRegisteredAt,
		String rawHash,
		String rawPayload,
		String sidoCodes,
		boolean nationwide,
		String categoryGroup,
		String duplicateKey,
		String sourceVersion,
		String checklistDocuments) {
}
