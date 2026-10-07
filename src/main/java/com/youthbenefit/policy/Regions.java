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

	private Regions() {
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
