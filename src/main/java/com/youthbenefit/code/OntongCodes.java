package com.youthbenefit.code;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 온통청년 공식 코드정의서(API코드정보.xlsx)의 자격 조건 코드. 사용자가 고를 수 있는 값만 둔다 —
 * "제한없음"(0013010, 0049010, 0055003, 0011009, 0014010)은 공고 쪽에서만 쓰는 값이라 뺀다.
 */
public final class OntongCodes {

	public static final Map<String, String> JOB = ordered(
			"0013001", "재직자",
			"0013002", "자영업자",
			"0013003", "미취업자",
			"0013004", "프리랜서",
			"0013005", "일용근로자",
			"0013006", "(예비)창업자",
			"0013007", "단기근로자",
			"0013008", "영농종사자",
			"0013009", "기타");

	public static final Map<String, String> SCHOOL = ordered(
			"0049001", "고졸 미만",
			"0049002", "고교 재학",
			"0049003", "고졸 예정",
			"0049004", "고교 졸업",
			"0049005", "대학 재학",
			"0049006", "대졸 예정",
			"0049007", "대학 졸업",
			"0049008", "석·박사",
			"0049009", "기타");

	public static final Map<String, String> MARRIAGE = ordered(
			"0055001", "기혼",
			"0055002", "미혼");

	public static final Map<String, String> MAJOR = ordered(
			"0011001", "인문계열",
			"0011002", "사회계열",
			"0011003", "상경계열",
			"0011004", "이학계열",
			"0011005", "공학계열",
			"0011006", "예체능계열",
			"0011007", "농산업계열",
			"0011008", "기타");

	public static final Map<String, String> SPECIAL_GROUP = ordered(
			"0014001", "중소기업",
			"0014002", "여성",
			"0014003", "기초생활수급자",
			"0014004", "한부모가정",
			"0014005", "장애인",
			"0014006", "농업인",
			"0014007", "군인",
			"0014008", "지역인재",
			"0014009", "기타");

	private OntongCodes() {
	}

	public static List<CodeOption> options(Map<String, String> codes) {
		return codes.entrySet().stream().map(e -> new CodeOption(e.getKey(), e.getValue())).toList();
	}

	private static Map<String, String> ordered(String... pairs) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put(pairs[i], pairs[i + 1]);
		}
		return java.util.Collections.unmodifiableMap(map);
	}

}
