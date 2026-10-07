package com.youthbenefit.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RegionsTest {

	private static final String ALL_SIDO = "11110,26110,27110,28110,30110,31110,36110,41111,51110,43111,44131,52111,12110,47111,48121,50110";

	@Test
	void groupsZipCodesBySido() {
		List<String> codes = Regions.sidoCodes("30110,30140,30170,11620");

		assertThat(codes).containsExactly("11", "30");
		assertThat(Regions.displayNames(false, codes)).containsExactly("서울", "대전");
	}

	@Test
	void allSixteenSidoMeansNationwide() {
		List<String> codes = Regions.sidoCodes(ALL_SIDO);

		assertThat(Regions.isNationwide(codes)).isTrue();
		assertThat(Regions.displayNames(true, codes)).containsExactly("전국");
	}

	@Test
	void missingOneSidoIsNotNationwide() {
		List<String> codes = Regions.sidoCodes(ALL_SIDO.replace(",50110", ""));

		assertThat(Regions.isNationwide(codes)).isFalse();
	}

	@Test
	void emptyZipCdHasNoRegion() {
		assertThat(Regions.sidoCodes(null)).isEmpty();
		assertThat(Regions.sidoCodes(" ")).isEmpty();
	}

}
