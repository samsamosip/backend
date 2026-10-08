package com.youthbenefit.eligibility;

import java.util.List;

/**
 * 맞춤 홈 목록. counts 는 필터와 상관없이 전체 판정 건수(화면 위 "조건 일치 N · 확인 필요 N").
 */
public record MatchesResponse(Counts counts, List<MatchItem> content, int page, int size, long totalElements,
		int totalPages) {

	public record Counts(long match, long needsCheck, long noMatch) {
	}

}
