package com.youthbenefit.policy;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
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
		String submissionDocuments,
		@JsonFormat(pattern = "HH:mm") LocalTime applyEndTime,
		String memo,
		Support support,
		String sourceUrl,
		List<Document> documents) {

	/** 제출 서류 한 줄 (공고 상세 "제출 서류" 목록, 준비함 체크리스트와 같은 쪼개기). guide 는 발급처, 모르면 null */
	public record Document(String name, boolean optional,
			com.youthbenefit.application.DocumentGuides.Guide guide) {
	}

	private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder()
		.build();

	/**
	 * 원문 보기 링크: 온통청년은 정책 상세 페이지, 학교 장학은 Harvester 가 준 원문 주소, 직접 등록은 사용자가 넣은 링크.
	 */
	static String sourceUrl(Policy p) {
		return switch (p.getSource()) {
			case ONTONG -> "https://www.youthcenter.go.kr/youthPolicy/ythPlcyTotalSearch/ythPlcyDetail/" + p.getSourceId();
			case SCHOOL -> {
				if (p.getRawPayload() == null) {
					yield null;
				}
				tools.jackson.databind.JsonNode urls = JSON.readTree(p.getRawPayload()).path("source_urls");
				yield urls.isArray() && !urls.isEmpty() ? urls.get(0).asString(null) : null;
			}
			case USER -> p.getApplyUrl();
		};
	}

	public static PolicyResponse from(Policy p) {
		return from(p, List.of());
	}

	public static PolicyResponse from(Policy p, List<Document> documents) {
		return new PolicyResponse(p.getId(), p.getSource(), p.getTitle(), p.getDescription(),
				p.getSupportContent(), split(p.getCategoryGroup()), p.getOrganization(), p.getApplyPeriodCode(),
				p.getApplyStartDate(), p.getApplyEndDate(), p.isNationwide(), regions(p),
				p.getSprtTrgtMinAge(), p.getSprtTrgtMaxAge(), p.getZipCd(), p.getEarnCndSeCd(), p.getEarnMaxAmt(),
				p.getJobCd(), p.getSchoolCd(), p.getMrgSttsCd(), p.getPlcyMajorCd(), p.getSbizCd(), p.getApplyUrl(),
				p.getReferenceUrl1(), p.getSubmissionDocuments(), p.getApplyEndTime(), p.getMemo(), Support.of(p), sourceUrl(p), documents);
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
