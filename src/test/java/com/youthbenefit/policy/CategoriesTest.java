package com.youthbenefit.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategoriesTest {

	@Test
	void mapsNewNamesToOfficialFive() {
		assertThat(Categories.normalize("금융･복지･문화")).containsExactly("복지문화");
		assertThat(Categories.normalize("교육･직업훈련")).containsExactly("교육");
		assertThat(Categories.normalize("참여･기반")).containsExactly("참여권리");
		assertThat(Categories.normalize("주거")).containsExactly("주거");
	}

	@Test
	void splitsAndDeduplicatesMultipleValues() {
		assertThat(Categories.normalize("교육,일자리")).containsExactly("일자리", "교육");
		assertThat(Categories.normalize("교육,교육･직업훈련")).containsExactly("교육");
		assertThat(Categories.normalize("일자리,일자리,일자리")).containsExactly("일자리");
	}

	@Test
	void keepsUnknownNamesAndHandlesBlank() {
		assertThat(Categories.normalize("주거,새분류")).containsExactly("주거", "새분류");
		assertThat(Categories.normalize("")).isEmpty();
		assertThat(Categories.normalize(null)).isEmpty();
	}

}
