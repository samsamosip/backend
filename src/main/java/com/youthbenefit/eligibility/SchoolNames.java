package com.youthbenefit.eligibility;

import java.util.ArrayList;
import java.util.List;

/**
 * 학교 이름 비교. 사용자가 "인하대", "인하 대학교"처럼 적어도 공고의 "인하대학교"와 같게 본다.
 * 괄호 안 다른 이름도 인정한다: "KAIST(한국과학기술원)" 은 "KAIST" 와 "한국과학기술원" 둘 다.
 */
final class SchoolNames {

	private SchoolNames() {
	}

	static boolean matches(String mine, String required) {
		String me = normalize(mine);
		return aliases(required).stream().map(SchoolNames::normalize).anyMatch(me::equals);
	}

	private static List<String> aliases(String name) {
		List<String> names = new ArrayList<>();
		int open = name.indexOf('(');
		int close = name.lastIndexOf(')');
		if (open > 0 && close > open) {
			names.add(name.substring(0, open));
			names.add(name.substring(open + 1, close));
		}
		else {
			names.add(name);
		}
		return names;
	}

	/** 공백을 빼고 대소문자를 무시하고, 끝의 "대학교"는 "대"로 줄인다. */
	static String normalize(String name) {
		String n = name.replaceAll("\\s", "").toLowerCase();
		return n.endsWith("대학교") ? n.substring(0, n.length() - 2) : n;
	}

}
