package com.youthbenefit.search;

import java.util.List;

/**
 * 검색 문장을 알아들은 결과. 화면에 "이렇게 찾았어요" 칩으로 보여 준다.
 *
 * @param keywords 찾는 것(필요한 지원) 단어 묶음 — 묶음 안의 말은 같은 뜻: [["월세", "주거비", "임차료"]]. 하나는 나와야 한다
 * @param audience 누구인지(대학생, 취준생…) 단어 묶음 — 나오면 점수만 더한다(자격은 프로필 판정이 본다)
 * @param categories 공식 대분류(일자리·주거·교육·복지문화·참여권리) 중 해당하는 것
 * @param region 지역 이름(화면용, 예: "서울", "인천 부평구"), 없으면 null
 * @param regionCodes 지역 코드(시도 2자리 또는 시군구 5자리)
 * @param age 나이(만), 없으면 null
 * @param closingSoon "마감 임박", "이번 주" → 7일 안에 마감
 * @param ai AI 가 해석했는지(false 면 단어로만 찾음)
 * @param regionFromProfile 문장에 지역이 없어서 내 프로필 지역을 쓴 경우(화면 칩 "내 지역 · 인천")
 */
public record SearchQuery(List<List<String>> keywords, List<List<String>> audience, List<String> categories,
		String region, List<String> regionCodes, Integer age, boolean closingSoon, boolean ai,
		boolean regionFromProfile) {

	public boolean hasFilters() {
		return !keywords.isEmpty() || !audience.isEmpty() || !categories.isEmpty() || !regionCodes.isEmpty() || age != null || closingSoon;
	}

	/** 지역 조건을 뺀 것 (결과 없을 때 "지역을 빼면 n개" 안내용) */
	SearchQuery withoutRegion() {
		return new SearchQuery(keywords, audience, categories, null, List.of(), age, closingSoon, ai, false);
	}

	SearchQuery withProfileRegion(String name, List<String> codes) {
		return new SearchQuery(keywords, audience, categories, name, codes, age, closingSoon, ai, true);
	}

	/**
	 * 화면에서 ×로 뺀 칩을 지운다 (디자인 06 "필요 없는 조건은 ×로 빼세요").
	 *
	 * @param dropWords 뺄 단어 묶음의 대표어(묶음 첫 단어)
	 */
	SearchQuery without(boolean noRegion, boolean noCategory, boolean noAge, boolean noClosingSoon,
			java.util.Collection<String> dropWords) {
		return new SearchQuery(keywords.stream().filter(g -> !dropWords.contains(g.get(0))).toList(),
				audience.stream().filter(g -> !dropWords.contains(g.get(0))).toList(),
				noCategory ? List.of() : categories, noRegion ? null : region, noRegion ? List.of() : regionCodes,
				noAge ? null : age, !noClosingSoon && closingSoon, ai, !noRegion && regionFromProfile);
	}

}
