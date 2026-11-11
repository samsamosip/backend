package com.youthbenefit.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicySource;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticSearchMergeTest {
	Policy policy(long id, PolicySource source, String sourceId, String category, boolean nationwide) {
		Policy p = mock(Policy.class);
		when(p.getId()).thenReturn(id); when(p.getSource()).thenReturn(source); when(p.getSourceId()).thenReturn(sourceId);
		when(p.getCategoryGroup()).thenReturn(category); when(p.isNationwide()).thenReturn(nationwide);
		when(p.getSidoCodes()).thenReturn("26");
		return p;
	}

	@Test
	void mergeKeepsGovernmentHitsAndRejectsUnknownAndFilteredSchoolHits() {
		Policy government = policy(1, PolicySource.ONTONG, "gov", "교육", true);
		Policy school = policy(2, PolicySource.SCHOOL, "school", "교육", true);
		Policy wrongRegion = policy(3, PolicySource.SCHOOL, "busan", "교육", false);
		Policy wrongCategory = policy(4, PolicySource.SCHOOL, "work", "일자리", true);
		Policy wrongAge = policy(5, PolicySource.SCHOOL, "age", "교육", true);
		when(wrongAge.getSprtTrgtMinAge()).thenReturn(30);
		Policy wrongDeadline = policy(6, PolicySource.SCHOOL, "deadline", "교육", true);
		when(wrongDeadline.getApplyEndDate()).thenReturn(LocalDate.now().plusDays(30));
		when(school.getApplyEndDate()).thenReturn(LocalDate.now().plusDays(3));
		SearchQuery q = new SearchQuery(List.of(List.of("학비")), List.of(), List.of("교육"), "서울", List.of("11"), 24, true, true, false);
		var lexical = List.of(new SearchService.Scored(government, 1, 3));
		var result = new HarvesterSemanticSearch.Result(true, List.of(
			new HarvesterSemanticSearch.Hit("school", .6), new HarvesterSemanticSearch.Hit("school", .7),
			new HarvesterSemanticSearch.Hit("busan", .8), new HarvesterSemanticSearch.Hit("work", .8),
			new HarvesterSemanticSearch.Hit("age", .8), new HarvesterSemanticSearch.Hit("deadline", .8),
			new HarvesterSemanticSearch.Hit("not-open-or-not-synced", .99), new HarvesterSemanticSearch.Hit("gov", .99)));
		var merged = SearchService.merge(List.of(government, school, wrongRegion, wrongCategory, wrongAge, wrongDeadline), lexical, q, LocalDate.now(), result);
		assertThat(merged.candidates()).extracting(s -> s.policy().getId()).containsExactly(1L, 2L);
		assertThat(merged.addedIds()).containsExactly(2L);
		assertThat(merged.scores()).containsOnlyKeys(2L);
	}

	@Test
	void semanticAgreementDoesNotDuplicateAnExistingLexicalSchoolHit() {
		Policy school = policy(2, PolicySource.SCHOOL, "school", "교육", true);
		SearchQuery q = new SearchQuery(List.of(List.of("학비")), List.of(), List.of(), null, List.of(), null, false, true, false);
		var lexical = List.of(new SearchService.Scored(school, 1, 3));
		var merged = SearchService.merge(List.of(school), lexical, q, LocalDate.now(),
			new HarvesterSemanticSearch.Result(true, List.of(new HarvesterSemanticSearch.Hit("school", .7))));
		assertThat(merged.candidates()).isEqualTo(lexical);
		assertThat(merged.addedIds()).isEmpty();
		assertThat(merged.scores()).containsEntry(2L, .7);
	}
}
