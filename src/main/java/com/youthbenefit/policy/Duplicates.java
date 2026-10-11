package com.youthbenefit.policy;

/**
 * 같은 공고가 번호만 바꿔 다시 등록된 것을 찾기 위한 키. 제목(띄어쓰기 무시)과 주관기관이 같으면 같은 공고로 본다.
 * 기관이 다르면 이름이 같아도 지역이 다른 별개 사업이라 묶지 않는다 (예: 시·도마다 있는 "청년문화예술패스").
 */
public final class Duplicates {

	private Duplicates() {
	}

	public static String key(String title, String organization) {
		if (title == null) {
			return null;
		}
		return title.replaceAll("\\s", "") + "|" + (organization == null ? "" : organization);
	}

}
