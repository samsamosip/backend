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

	@Test
	void sigunguNamesAndSelectableCodes() {
		assertThat(Regions.displayName("11620")).isEqualTo("서울 관악구");
		assertThat(Regions.displayName("12330")).isEqualTo("전남광주 광산구");
		assertThat(Regions.displayName("36110")).isEqualTo("세종");
		assertThat(Regions.displayName("28")).isEqualTo("인천");
		// 없어진 인천 중구, 구로 나뉜 화성시는 고를 수 없다
		assertThat(Regions.isSelectable("28185")).isTrue();
		assertThat(Regions.isSelectable("28110")).isFalse();
		assertThat(Regions.isSelectable("41590")).isFalse();
		assertThat(Regions.isSelectable("99999")).isFalse();
		assertThat(Regions.selectableBySido().values().stream().mapToInt(List::size).sum()).isEqualTo(256);
	}

	@Test
	void oldAndCityCodesCoverTheirSuccessors() {
		assertThat(Regions.withSuccessors(List.of("41590", "11620"))).containsExactly("41590", "11620", "41591", "41593",
				"41595", "41597");
		assertThat(Regions.withSuccessors(List.of("28260"))).containsExactly("28260", "28275", "28290");
	}

	@Test
	void resolvesResidenceNamesFromScholarships() {
		assertThat(Regions.resolve("인천광역시")).containsExactly("28");
		assertThat(Regions.resolve("춘천시")).containsExactly("51110");
		assertThat(Regions.resolve("울산 남구")).containsExactly("31140");
		assertThat(Regions.resolve("광주광역시 광산구")).containsExactly("12330");
		assertThat(Regions.resolve("천안")).containsExactly("44131", "44133");
		assertThat(Regions.resolve("용인시")).containsExactly("41461", "41463", "41465");
		// 여러 곳에 있는 이름, 동 단위는 정할 수 없다
		assertThat(Regions.resolve("남구")).isEmpty();
		assertThat(Regions.resolve("고성군")).isEmpty();
		assertThat(Regions.resolve("석남1동")).isEmpty();
		assertThat(Regions.resolve("남동구 간석3동")).isEmpty();
	}

	@Test
	void districtsOfOneCityAreShownAsTheCity() {
		assertThat(Regions.displayNamesOf(List.of("41461", "41463", "41465", "28"))).containsExactly("경기 용인시", "인천");
		assertThat(Regions.displayNamesOf(List.of("41463"))).containsExactly("경기 용인시 기흥구");
	}

}
