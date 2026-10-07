package com.youthbenefit.policy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 온통청년 대분류(lclsfNm)를 공식 코드정의서의 5개로 묶는다. 실측(2026-10)에서 옛 이름과 새 이름이 섞여 있고
 * ("교육" / "교육･직업훈련"), 한 정책에 여러 개가 콤마로 들어오기도 한다 ("일자리,교육").
 */
public final class Categories {

	/** 공식 대분류, 화면 표시 순서. */
	public static final List<String> OFFICIAL = List.of("일자리", "주거", "교육", "복지문화", "참여권리");

	// 온통청년 데이터의 가운뎃점은 반각 가타카나 중점(U+FF65)이다
	private static final Map<String, String> ALIASES = Map.of(
			"교육･직업훈련", "교육",
			"금융･복지･문화", "복지문화",
			"참여･기반", "참여권리");

	private Categories() {
	}

	/** "금융･복지･문화,금융･복지･문화" → ["복지문화"]. 모르는 이름은 그대로 뒤에 붙인다. */
	public static List<String> normalize(String lclsfNm) {
		if (lclsfNm == null || lclsfNm.isBlank()) {
			return List.of();
		}
		List<String> names = Arrays.stream(lclsfNm.split(","))
			.map(String::strip)
			.filter(name -> !name.isEmpty())
			.map(name -> ALIASES.getOrDefault(name, name))
			.distinct()
			.toList();
		List<String> official = OFFICIAL.stream().filter(names::contains).toList();
		List<String> others = names.stream().filter(name -> !OFFICIAL.contains(name)).toList();
		return java.util.stream.Stream.concat(official.stream(), others.stream()).toList();
	}

}
