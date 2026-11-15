package com.youthbenefit.search;

import java.util.List;

/** 실제 공고 주제에 대응하는 예시. 추천과 실행에서 같은 조건을 사용하며 AI를 호출하지 않는다. */
final class SearchExamples {

	private SearchExamples() {
	}

	record Example(String text, SearchQuery query) {
	}

	static final List<Example> TOPICS = List.of(
			example("월세 지원받을 수 있는 거 있어?", List.of(List.of("월세", "주거비", "임차료")), List.of(), List.of("주거")),
			example("자격증 응시료 지원받을 수 있어?", List.of(List.of("자격증 응시료", "자격시험 응시료", "자격시험 응시비", "응시료", "응시비")), List.of(), List.of()),
			example("면접 정장 빌릴 수 있는 지원 있어?", List.of(List.of("면접 정장", "면접복", "정장 대여")), List.of(), List.of("일자리")),
			example("대학생 등록금 지원받을 수 있어?", List.of(List.of("등록금 지원", "학비 지원", "장학금")), List.of(List.of("대학생", "재학생")), List.of("교육")),
			example("주택 보증금 대출이자 지원 있어?", List.of(List.of("주택 보증금 대출이자", "전세자금 대출이자", "임차보증금 대출이자", "전월세 보증금 이자 지원")), List.of(), List.of("주거")),
			example("청년 이사비 지원받을 수 있어?", List.of(List.of("이사비", "이사 비용", "이사 지원"), List.of("중개보수", "부동산 중개수수료")), List.of(), List.of("주거")),
			example("청년 창업 지원받을 수 있어?", List.of(List.of("창업", "예비창업", "스타트업", "사업화")), List.of(), List.of("일자리")));

	static final Example GENERAL = example("내 조건에 맞는 혜택 찾아줘", List.of(), List.of(), List.of());

	private static Example example(String text, List<List<String>> keywords, List<List<String>> audience, List<String> categories) {
		return new Example(text, new SearchQuery(keywords, audience, categories, null, List.of(), null, false, false, false));
	}

	static SearchQuery query(String text) {
		if (GENERAL.text().equals(text)) return GENERAL.query();
		return TOPICS.stream().filter(example -> example.text().equals(text)).map(Example::query).findFirst().orElse(null);
	}

}
