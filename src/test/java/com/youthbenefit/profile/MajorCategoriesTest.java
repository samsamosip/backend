package com.youthbenefit.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MajorCategoriesTest {

	@Test
	void mapsMajorNamesToCategories() {
		assertThat(MajorCategories.codeOf("전기전자공학")).isEqualTo("0011005");
		assertThat(MajorCategories.codeOf("컴퓨터공학과")).isEqualTo("0011005");
		assertThat(MajorCategories.codeOf("경영학과")).isEqualTo("0011003");
		assertThat(MajorCategories.codeOf("국어국문학")).isEqualTo("0011001");
		assertThat(MajorCategories.codeOf("사회복지학")).isEqualTo("0011002");
		assertThat(MajorCategories.codeOf("간호학")).isEqualTo("0011004");
		assertThat(MajorCategories.codeOf("산업디자인")).isEqualTo("0011006");
		assertThat(MajorCategories.codeOf("원예산업")).isEqualTo("0011007");
		assertThat(MajorCategories.codeOf("모르는전공")).isNull();
		assertThat(MajorCategories.codeOf(" ")).isNull();
	}

}
