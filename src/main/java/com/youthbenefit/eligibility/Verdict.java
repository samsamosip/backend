package com.youthbenefit.eligibility;

/** 공고 전체 판정. 화면 표시는 조건 일치 / 확인 필요 / 조건 불일치. */
public enum Verdict {

	MATCH("조건 일치"), NEEDS_CHECK("확인 필요"), NO_MATCH("조건 불일치");

	private final String label;

	Verdict(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	static Verdict of(Truth truth) {
		return switch (truth) {
			case TRUE -> MATCH;
			case FALSE -> NO_MATCH;
			case UNKNOWN -> NEEDS_CHECK;
		};
	}

}
