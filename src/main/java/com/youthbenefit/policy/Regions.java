package com.youthbenefit.policy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시군구 코드(zipCd) 앞 2자리 = 시도. 실측(2026-10)으로 온통청년에 쓰이는 시도는 16개다 (전남 46·광주 29 → 전남광주 12로 통합).
 * 전국 정책도 "전국"이 아니라 시군구 코드를 전부 나열하므로, 16개 시도를 모두 포함하면 전국으로 본다.
 */
public final class Regions {

	private static final Map<String, String> SIDO_NAMES = new LinkedHashMap<>();

	static {
		SIDO_NAMES.put("11", "서울");
		SIDO_NAMES.put("26", "부산");
		SIDO_NAMES.put("27", "대구");
		SIDO_NAMES.put("28", "인천");
		SIDO_NAMES.put("30", "대전");
		SIDO_NAMES.put("31", "울산");
		SIDO_NAMES.put("36", "세종");
		SIDO_NAMES.put("41", "경기");
		SIDO_NAMES.put("51", "강원");
		SIDO_NAMES.put("43", "충북");
		SIDO_NAMES.put("44", "충남");
		SIDO_NAMES.put("52", "전북");
		SIDO_NAMES.put("12", "전남광주");
		SIDO_NAMES.put("47", "경북");
		SIDO_NAMES.put("48", "경남");
		SIDO_NAMES.put("50", "제주");
	}

	/** 시도 이름(정식·줄임) → 코드. 광주·전남은 통합 코드 12. 학교 장학 거주지 조건("인천", "제주특별자치도")에 쓴다. */
	private static final Map<String, String> SIDO_ALIASES = new LinkedHashMap<>();

	static {
		alias("11", "서울", "서울시", "서울특별시");
		alias("26", "부산", "부산시", "부산광역시");
		alias("27", "대구", "대구시", "대구광역시");
		alias("28", "인천", "인천시", "인천광역시");
		alias("30", "대전", "대전시", "대전광역시");
		alias("31", "울산", "울산시", "울산광역시");
		alias("36", "세종", "세종시", "세종특별자치시");
		alias("41", "경기", "경기도");
		alias("51", "강원", "강원도", "강원특별자치도");
		alias("43", "충북", "충청북도");
		alias("44", "충남", "충청남도");
		alias("52", "전북", "전라북도", "전북특별자치도");
		alias("12", "광주", "광주시", "광주광역시", "전남", "전라남도", "전남광주", "전남광주통합특별시");
		alias("47", "경북", "경상북도");
		alias("48", "경남", "경상남도");
		alias("50", "제주", "제주도", "제주특별자치도");
	}

	private static void alias(String code, String... names) {
		for (String name : names) {
			SIDO_ALIASES.put(name, code);
		}
	}

	private Regions() {
	}

	/** "인천광역시" → "28". 시도 이름이 아니면(시군구·동 이름 등) null. */
	public static String sidoCodeOf(String name) {
		return name == null ? null : SIDO_ALIASES.get(name.replaceAll("\\s", ""));
	}

	/** zipCd 콤마 목록 → 시도 코드 목록 (위 표 순서, 중복 없음). */
	public static List<String> sidoCodes(String zipCd) {
		if (zipCd == null || zipCd.isBlank()) {
			return List.of();
		}
		List<String> prefixes = Arrays.stream(zipCd.split(","))
			.map(String::strip)
			.filter(code -> code.length() >= 2)
			.map(code -> code.substring(0, 2))
			.distinct()
			.toList();
		List<String> known = SIDO_NAMES.keySet().stream().filter(prefixes::contains).toList();
		List<String> unknown = prefixes.stream().filter(p -> !SIDO_NAMES.containsKey(p)).sorted().toList();
		return java.util.stream.Stream.concat(known.stream(), unknown.stream()).toList();
	}

	public static boolean isNationwide(List<String> sidoCodes) {
		return sidoCodes.containsAll(SIDO_NAMES.keySet());
	}

	/** 화면 표시용 이름. 전국이면 ["전국"]. */
	public static List<String> displayNames(boolean nationwide, List<String> sidoCodes) {
		if (nationwide) {
			return List.of("전국");
		}
		return sidoCodes.stream().map(code -> SIDO_NAMES.getOrDefault(code, code)).toList();
	}

}
